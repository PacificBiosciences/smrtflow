// FIXME this is mostly redundant

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
trait FastaConverterBase extends LazyLogging{
  // The header is defined as the first space, ">my-header-value this is metadata"
  case class PacBioFastaRecord(header: String, metadata: Option[String], bases: Seq[Char], recordIndex: Int)


  def validateCharNotInHeader(char: Char): PacBioFastaRecord => Either[InValidFastaFileError, PacBioFastaRecord] = {

    def validateC(fastaRecord: PacBioFastaRecord): Either[InValidFastaFileError, PacBioFastaRecord] = {
      if (fastaRecord.header contains char) {
        Left(InValidFastaFileError(s"Header contains '$char'"))
      } else {
        Right(fastaRecord)
      }
    }
    validateC
  }

  val validateNoDoubleQuote = validateCharNotInHeader('\"')
  val validateNoColon = validateCharNotInHeader(':')
  val validateNoTab = validateCharNotInHeader('\t')

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
 *
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
 *
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

    // FIXME. Hack to get this to compose
  protected def handleCmdError(e: Either[ExternalCmdFailure, Path]): Either[InValidFastaFileError, Path] = {
    e match {
      case Right(p) => Right(p)
      case Left(ex) => Left(InValidFastaFileError(ex.msg))
    }
  }
}
