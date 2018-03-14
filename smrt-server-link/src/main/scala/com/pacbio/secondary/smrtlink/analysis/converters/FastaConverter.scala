package com.pacbio.secondary.smrtlink.analysis.converters

import java.nio.file.{Files, Path, Paths}
import java.io.{FileInputStream, FileOutputStream}
import java.text.SimpleDateFormat
import java.util.{UUID, Calendar}
import javax.xml.datatype.DatatypeFactory

import scala.util.{Try, Success, Failure}

// this is included in smrt-analysis
import org.broad.igv.feature.genome.FastaUtils

import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DatasetIndexFile,
  DataSetIO,
  ContigSetIO
}

// auto-generated Java modules
import com.pacificbiosciences.pacbiobasedatamodel.{
  ExternalResource,
  InputOutputDataType,
  ExternalResources
}
import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiodatasets.{
  DataSetMetadataType,
  ContigSetMetadataType,
  DataSetType,
  ContigSet
}

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.language.postfixOps

trait FastaIndexWriter {
  def createFaidx(fastaPath: Path): String = {
    val fastaPathStr = fastaPath.toAbsolutePath.toString
    val outputPath = fastaPathStr + ".fai"
    FastaUtils.createIndexFile(fastaPathStr, outputPath)
    if (!Files.exists(Paths.get(outputPath)))
      throw new Exception(s".fai file not written: $outputPath")
    outputPath
  }
}

/**
  * Base functions for converting a FASTA file to a DataSet
  */
trait FastaConverterBase[
    T <: DataSetType, U <: DataSetMetadataType, V <: DataSetIO]
    extends LazyLogging
    with FastaIndexWriter {

  protected val baseName: String // reference
  protected val dsName: String // ReferenceSet
  protected val programName: String // fasta-to-reference
  protected val metatype: String // PacBio.DataSet.ReferenceSet
  protected val fastaMetatype: String // PacBio.ReferenceFile.ReferenceFastaFile
  protected val baseTags: Seq[String] = Seq("converted")

  protected def nameToFileName(name: String): String =
    name.replaceAll("[^A-Za-z0-9_]", "_")

  protected def createIndexFiles(fastaPath: Path): Seq[DatasetIndexFile] = {
    Seq(DatasetIndexFile(FileTypes.I_SAM.fileTypeId, createFaidx(fastaPath)))
  }

  protected def composeMetaData(refMetaData: ContigsMetaData)(
      implicit man: Manifest[U]): U = {
    val metadata = man.runtimeClass.newInstance.asInstanceOf[U]
    metadata.setNumRecords(refMetaData.nrecords)
    metadata.setTotalLength(refMetaData.totalLength)
    metadata
  }

  // FIXME workaround for lack of DataSetType.setDataSetMetaData
  // this is a little hacky - there is probably a cleaner way to do it with
  // implicits and/or type classes
  protected def setMetadata(ds: T, metadata: U): Unit

  protected def composeDataSet(fastaPath: Path,
                               name: String,
                               outputDir: Path,
                               metadata: U,
                               relativePath: Boolean = true,
                               makeIndices: Path => Seq[DatasetIndexFile] =
                                 createIndexFiles)(
      implicit man: Manifest[T]): T = {
    val timeStamp = new SimpleDateFormat("yyMMdd_HHmmss")
      .format(Calendar.getInstance().getTime)
    def toTimeStampName(n: String) = s"${n}_$timeStamp"

    // This is so clumsy
    val uuid = UUID.randomUUID()
    val createdAt = DatatypeFactory
      .newInstance()
      .newXMLGregorianCalendar(new JodaDateTime().toGregorianCalendar)
    val timeStampName = toTimeStampName(dsName.toLowerCase)
    val fastaTimeStampName = toTimeStampName("fasta")
    val tags = (baseTags ++ Seq(baseName)).mkString(", ")
    val description = s"Converted $dsName $name"

    val er = new ExternalResource()
    er.setCreatedAt(createdAt)
    er.setModifiedAt(createdAt)
    er.setMetaType(fastaMetatype)
    er.setName(s"Fasta $name")
    er.setUniqueId(UUID.randomUUID().toString)
    er.setTags(tags)
    er.setDescription(s"Converted with $programName")
    er.setTimeStampedName(fastaTimeStampName)
    if (relativePath) {
      er.setResourceId(outputDir.relativize(fastaPath.toAbsolutePath).toString)
    } else {
      er.setResourceId(fastaPath.toAbsolutePath.toString)
    }

    val fileIndices = new FileIndices()
    for (indexFile <- makeIndices(fastaPath)) {
      val idx = new InputOutputDataType()
      idx.setUniqueId(UUID.randomUUID().toString)
      idx.setTimeStampedName(toTimeStampName("index"))
      idx.setResourceId(
        outputDir.relativize(Paths.get(indexFile.url)).toString)
      idx.setMetaType(indexFile.indexType)
      fileIndices.getFileIndex.add(idx)
    }
    er.setFileIndices(fileIndices)

    val externalResources = new ExternalResources()
    externalResources.getExternalResource.add(er)

    val ds = man.runtimeClass.newInstance.asInstanceOf[T]
    ds.setVersion(CommonConstants.DATASET_VERSION)
    ds.setMetaType(metatype)
    ds.setCreatedAt(createdAt)
    ds.setModifiedAt(createdAt)
    ds.setTimeStampedName(timeStampName)
    ds.setUniqueId(uuid.toString)
    ds.setName(name)
    ds.setDescription(description)
    ds.setTags(tags)
    setMetadata(ds, metadata) // XXX HACK
    ds.setExternalResources(externalResources)
    ds
  }

  case class TargetInfo(name: String,
                        dataDir: Path,
                        fastaPath: Path,
                        dsFile: Path)

  protected def setupTargetDir(name: String,
                               fastaPath: Path,
                               outputDir: Path,
                               inPlace: Boolean = false,
                               mkdir: Boolean = false): TargetInfo = {
    if (mkdir && (!Files.exists(outputDir))) outputDir.toFile.mkdir()
    val sanitizedName = nameToFileName(name)
    val targetDir = outputDir.resolve(sanitizedName).toAbsolutePath
    if (Files.exists(targetDir))
      throw DatasetConvertError(
        s"The directory ${targetDir} already exists -please remove it or specify an alternate output directory or reference name.")
    targetDir.toFile.mkdir()
    var fastaFinal = fastaPath
    if (!inPlace) {
      targetDir.resolve("sequence").toFile().mkdir
      fastaFinal = targetDir.resolve(s"sequence/${sanitizedName}.fasta")
      new FileOutputStream(fastaFinal.toFile()) getChannel () transferFrom (new FileInputStream(
        fastaPath.toFile()) getChannel, 0, Long.MaxValue)
    }
    val ofn = outputDir.resolve(s"${sanitizedName}/${dsName.toLowerCase}.xml")
    TargetInfo(sanitizedName, targetDir, fastaFinal, ofn)
  }
}

trait ReferenceConverterBase[
    T <: DataSetType, U <: DataSetMetadataType, V <: DataSetIO]
    extends FastaConverterBase[T, U, V] {

  def apply(name: String,
            organism: Option[String],
            ploidy: Option[String],
            fastaPath: Path,
            outputDir: Path,
            inPlace: Boolean,
            mkdir: Boolean): Either[DatasetConvertError, V]

  def toTry(name: String,
            organism: Option[String],
            ploidy: Option[String],
            fastaPath: Path,
            outputDir: Path,
            mkdir: Boolean = false): Try[V] = {
    apply(name,
          organism,
          ploidy,
          fastaPath,
          outputDir,
          inPlace = false,
          mkdir = mkdir).toTry
  }
}
