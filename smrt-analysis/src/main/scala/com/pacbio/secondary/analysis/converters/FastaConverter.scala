package com.pacbio.secondary.analysis.converters

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.UUID

import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.{ReferenceDataset, DataSetMetaData, DatasetIndexFile, ReferenceDatasetIO}
import com.pacbio.secondary.analysis.externaltools.{ExternalCmdFailure, CallSaWriterIndex, CallSamToolsIndex}
import com.pacbio.secondary.analysis.legacy.ReferenceContig
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, InputOutputDataType, ExternalResources}
import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiodatasets.{ContigSetMetadataType, ReferenceSet}
import com.typesafe.scalalogging.LazyLogging
import htsjdk.samtools.reference.{FastaSequenceFile, ReferenceSequence}
import org.joda.time.{DateTime=> JodaDateTime}

import scala.collection.mutable

/**
 * Converts a Fasta File to a Reference DataSet
 * Created by mkocher on 9/26/15.
 */
object FastaConverter extends LazyLogging{


  /**
   * 1. Validate contigs
   * 2. Copy Fasta file to directory
   * 3. Create fasta index via samtools
   * 4. Call sawriter index via sawriter
   * 5. Write reference.dataset.xml
   *
   * @param fastaPath Fasta File
   * @param outputDir Output of the directory of Reference Repo
   * @return
   */
  def createReferenceFromFasta(fastaPath: Path,
                               outputDir: Path,
                               name: Option[String],
                               organism: Option[String],
                               ploidy: Option[String]): Either[FastaConversionError, ReferenceDatasetIO] = {

    val seqOutputDir = outputDir.resolve("sequence")

    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir)
    }

    logger.debug(s"Loading fasta file from $fastaPath")

    // FIXME. Hack to get this to compose
    def toE(e: Either[ExternalCmdFailure, Path]): Either[InValidFastaFileError, Path] = {
      e match {
        case Right(p) => Right(p)
        case Left(ex) => Left(InValidFastaFileError(ex.msg))
      }
    }
    val fx = for {
      contigs <- validateFastaFile(fastaPath).right
      faiIndex <- toE(CallSamToolsIndex.run(fastaPath)).right
      saIndex <- toE(CallSaWriterIndex.run(fastaPath)).right
    } yield (contigs, faiIndex, saIndex)


    fx match {
      case Left(ex) =>
        Left(ex)
      case Right(xs) =>
        val contigs = xs._1
        val faiIndex = xs._2
        val saIndex = xs._3

        val nrecords = contigs.length
        val totalLength = contigs.foldLeft(0)((m, n) => m + n.length)


        val indexFiles = Seq(
          DatasetIndexFile(FileTypes.I_SAW.fileTypeId, saIndex.toAbsolutePath.toString),
          DatasetIndexFile(FileTypes.I_SAM.fileTypeId, faiIndex.toAbsolutePath.toString)
        )

        val dsUUID = UUID.randomUUID()
        val unknown = "Unknown"

        val createdAt = JodaDateTime.now().toString
        // Version
        val tags = Seq("converted-reference")
        val refName = name getOrElse unknown
        val comments = "Converted Fasta Reference Comment"
        val organismName = organism getOrElse unknown
        val ploidyName = ploidy getOrElse unknown

        val metadata = DataSetMetaData(
          dsUUID,
          refName,
            CommonConstants.DATASET_VERSION,
          createdAt,
          tags,
          comments,
          nrecords,
          totalLength)

        val ds = ReferenceDataset(organismName, ploidyName, contigs, metadata)
        val rio = ReferenceDatasetIO(fastaPath.toAbsolutePath.toString, ds, indexFiles)

        val dsXmlPath = outputDir.resolve("reference.dataset.xml")
        val z = ReferenceDataset.writeFile(rio, dsXmlPath)

        logger.info(s"Converted Fasta to ReferenceSet ${dsUUID.toString} to ${dsXmlPath.toString}")
        Right(rio)
    }
  }

  // The header is defined as the first space, ">my-header-value this is metadata"
  case class PacBioFastaRecord(header: String, metadata: Option[String], bases: Seq[Char], recordIndex: Int)


  def _validateCharNotInHeader(char: Char): PacBioFastaRecord => Either[InValidFastaFileError, PacBioFastaRecord] = {

    def validateC(fastaRecord: PacBioFastaRecord): Either[InValidFastaFileError, PacBioFastaRecord] = {
      if (fastaRecord.header contains char) {
        Left(InValidFastaFileError(s"Header contains '$char'"))
      } else {
        Right(fastaRecord)
      }
    }
    validateC
  }

  val validateNoDoubleQuote = _validateCharNotInHeader('\"')
  val validateNoColon = _validateCharNotInHeader(':')
  val validateNoTab = _validateCharNotInHeader('\t')

  /**
   * Validate a PacBio Fasta Record
   * Specific Validation of header
   * \t is not allowed
   * '"' is not allowed
   * ':' is not allowed
   * 'id's are unique in the entire file
   * non-empty file
   * At least one fasta record
   * Using Either to propagate up Error messages
   *
   * Bases must only contain IUPAC (or lowercase versions)
   * @param fastaRecord
   * @return
   */
  def validateFastaRecord(fastaRecord: PacBioFastaRecord): Either[InValidFastaFileError, PacBioFastaRecord] = {

    for {
      r1 <- validateNoTab(fastaRecord).right
      r2 <- validateNoColon(r1).right
      r3 <- validateNoDoubleQuote(r2).right
    } yield r3
  }

  def seqRecordToPacBioFastaRecord(referenceSequence: ReferenceSequence): PacBioFastaRecord = {
    //FIXME. Add validation to bases (only IUPAC?)
    val bases = referenceSequence.getBases.toList.map(_.toChar)
    PacBioFastaRecord(referenceSequence.getName, None, bases, referenceSequence.getContigIndex)
  }

  /**
   * Validate that Fasta file is compliant with PacBio Spec
   * @param path
   * @return
   */
  def validateFastaFile(path: Path): Either[InValidFastaFileError, Seq[ReferenceContig]] = {
    logger.info(s"Loading fasta file $path")

    // Probably want this to be false so manually parse the raw header
    val truncateNamesAtWhitespace = true
    val f = new FastaSequenceFile(path.toFile, truncateNamesAtWhitespace)

    var toBreak = false

    val headerIds = mutable.Set[String]()
    val errors = mutable.MutableList[String]()

    val contigs = mutable.MutableList[ReferenceContig]()
    val digest = MessageDigest.getInstance("MD5")

    while (!toBreak) {
      val xseq = f.nextSequence()
      xseq match {
        case xs: ReferenceSequence =>
          val results = validateFastaRecord(seqRecordToPacBioFastaRecord(xs))
          results match {
            case Right(x) =>
              // Check if the header is already in another record
              headerIds.add(xs.getName)
              // FIXME Is this what the original pbcore version using?
              val md5 = digest.digest(xs.getName.getBytes).map("%02x".format(_)).mkString
              val contig = ReferenceContig(xs.getName, s"Description ${xs.getName}-${xs.getContigIndex}", xs.getBases.length, md5)
              contigs += contig
            // write contig details to ReferenceSet.xml
            case Left(er) =>
              println(s"Failed to valid ${er.msg}")
              errors += er.msg
              toBreak = true

          }
        case _ => toBreak = true
      }
    }
    f.close()

    if (errors.isEmpty) {
      Right(contigs)
    } else {
      Left(InValidFastaFileError(errors.toString()))
    }
  }

  /**
   * This is the new model for writing a ReferenceSet using jaxb
   * This should eventually replace the manual XML ReferenceSet creation
   * @param contigs
   * @return
   */
  def contigsToReferenceSet(contigs: Seq[ReferenceContig]): ReferenceSet = {

    // Containers
    val externalResources = new ExternalResources()
    val fileIndices = new FileIndices()

    // FIXME FileIndex
    // Sam Index .fai
    val inputData = new InputOutputDataType()
    inputData.setDescription("SamTools Index")
    inputData.setResourceId("../path/to/sam.index.")
    inputData.setMetaType(FileTypes.I_SAM.toString)
    inputData.setUniqueId(UUID.randomUUID().toString)

    fileIndices.getFileIndex.add(inputData)

    //


    val er = new ExternalResource()
    er.setName("my-resource")
    er.setFileIndices(fileIndices)

    externalResources.getExternalResource.add(er)

    val rs = new ReferenceSet()
    rs.setUniqueId(UUID.randomUUID().toString)
    rs.setDescription(s"Converted dataset")
    rs.setExternalResources(externalResources)

    val md = new ContigSetMetadataType()
    md.setOrganism("Organism")
    md.setPloidy("Haploid")
    md.setNumRecords(-1)
    md.setTotalLength(-1)
    // Add contigs here
    rs.setDataSetMetadata(md)
    rs
  }

  def fastaToReferenceSet(path: Path): Either[InValidFastaFileError, ReferenceSet] = {

    val rs = new ReferenceSet()
    rs.setDescription(s"Converted dataset from $path")

    val result = validateFastaFile(path)
    result match {
      case Right(contigs) => Right(contigsToReferenceSet(contigs))
      case Left(ex) => Left(ex)
    }
  }
  
}
