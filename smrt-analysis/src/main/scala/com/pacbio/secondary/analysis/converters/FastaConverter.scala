
package com.pacbio.secondary.analysis.converters

import java.nio.file.{Files, Path, Paths}
import java.io.{FileInputStream,FileOutputStream}
import java.text.SimpleDateFormat
import java.util.{UUID, Calendar}
import javax.xml.datatype.DatatypeFactory

import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.DatasetIndexFile
import com.pacbio.secondary.analysis.externaltools.CallSamToolsIndex

// auto-generated Java modules
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, InputOutputDataType, ExternalResources}
import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiodatasets.{DataSetMetadataType, ContigSetMetadataType, DataSetType, ContigSet}

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime=> JodaDateTime}

import scala.language.postfixOps

/**
 * Base functions for converting a FASTA file to a DataSet
 */
trait FastaConverterBase[T <: DataSetType, U <: DataSetMetadataType] extends LazyLogging {

  protected val baseName: String // reference
  protected val dsName: String // ReferenceSet
  protected val programName: String // fasta-to-reference
  protected val metatype: String // PacBio.DataSet.ReferenceSet

  protected def nameToFileName(name: String): String = name.replaceAll("[^A-Za-z0-9_]", "_")

  protected def createIndexFiles(fastaPath: Path): Seq[DatasetIndexFile] = {
    val faiIndex = CallSamToolsIndex.run(fastaPath) match {
      case Right(f) => f.toAbsolutePath
      case Left(err) => throw new Exception(s"samtools index failed: ${err.getMessage}")
    }
    Seq(DatasetIndexFile(FileTypes.I_SAM.fileTypeId, faiIndex.toAbsolutePath.toString))
  }

  protected def composeMetaData(refMetaData: ContigsMetaData)
                               (implicit man: Manifest[U]): U = {
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
                               metadata: U)
                              (implicit man: Manifest[T]): T = {
    val timeStamp = new SimpleDateFormat("yyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toTimeStampName(n: String) = s"${n}_$timeStamp"

    // This is so clumsy
    val uuid = UUID.randomUUID()
    val createdAt = DatatypeFactory.newInstance().newXMLGregorianCalendar(new JodaDateTime().toGregorianCalendar)
    val timeStampName = toTimeStampName(dsName.toLowerCase)
    val fastaTimeStampName = toTimeStampName("fasta")
    val tags = s"converted, $baseName"
    val description = s"Converted $dsName $name"

    val er = new ExternalResource()
    er.setCreatedAt(createdAt)
    er.setModifiedAt(createdAt)
    er.setMetaType(FileTypes.FASTA_REF.fileTypeId)
    er.setName(s"Fasta $name")
    er.setUniqueId(UUID.randomUUID().toString)
    er.setTags(tags)
    er.setDescription(s"Converted with $programName")
    er.setTimeStampedName(fastaTimeStampName)
    er.setResourceId(outputDir.relativize(fastaPath.toAbsolutePath).toString)

    val fileIndices = new FileIndices()
    for (indexFile <- createIndexFiles(fastaPath)) {
      val idx = new InputOutputDataType()
      idx.setUniqueId(UUID.randomUUID().toString)
      idx.setTimeStampedName(toTimeStampName("index"))
      idx.setResourceId(outputDir.relativize(Paths.get(indexFile.url)).toString)
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

  case class TargetInfo(name: String, dataDir: Path, fastaPath: Path, dsFile: Path)

  protected def setupTargetDir(name: String, fastaPath: Path, outputDir: Path,
                               inPlace: Boolean = false,
                               mkdir: Boolean = false): TargetInfo = {
    if (mkdir && (! Files.exists(outputDir))) outputDir.toFile.mkdir()
    val sanitizedName = nameToFileName(name)
    val targetDir = outputDir.resolve(sanitizedName).toAbsolutePath
    if (Files.exists(targetDir)) throw DatasetConvertError(s"The directory ${targetDir} already exists -please remove it or specify an alternate output directory or reference name.")
    targetDir.toFile.mkdir()
    var fastaFinal = fastaPath
    if (! inPlace) {
      targetDir.resolve("sequence").toFile().mkdir
      fastaFinal = targetDir.resolve(s"sequence/${sanitizedName}.fasta")
      new FileOutputStream(fastaFinal.toFile()) getChannel() transferFrom(
        new FileInputStream(fastaPath.toFile()) getChannel, 0, Long.MaxValue)
    }
    val ofn = outputDir.resolve(s"${sanitizedName}/${dsName.toLowerCase}.xml")
    TargetInfo(sanitizedName, targetDir, fastaFinal, ofn)
  }

}

// XXX This is just a stub right now.
object FastaContigsConverter extends FastaConverterBase[ContigSet, ContigSetMetadataType] {
  protected val baseName: String = "contigs"
  protected val dsName: String = "ContigSet"
  protected val programName: String = "unknown"
  protected val metatype: String = FileTypes.DS_CONTIG.fileTypeId

  override protected def setMetadata(ds: ContigSet, metadata: ContigSetMetadataType): Unit = ds.setDataSetMetadata(metadata)
}
