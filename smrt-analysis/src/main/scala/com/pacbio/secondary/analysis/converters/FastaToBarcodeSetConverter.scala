
package com.pacbio.secondary.analysis.converters

import java.nio.file.{Files, Path, Paths}
import java.io.{File,FileInputStream,FileOutputStream}
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.{UUID, Calendar}
import javax.xml.datatype.DatatypeFactory

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import spray.json._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets._
import com.pacbio.secondary.analysis.externaltools.{CallSamToolsIndex, ExternalCmdFailure, ExternalToolsUtils}
import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.analysis.datasets.io.DataSetWriter

import com.pacbio.secondary.analysis.legacy.ReferenceContig
import com.pacbio.secondary.analysis.referenceUploader.ReposUtils
import com.pacificbiosciences.pacbiodatasets.Contigs.Contig
import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiodatasets.{BarcodeSetMetadataType, Contigs, BarcodeSet}
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, InputOutputDataType, ExternalResources}


object FastaBarcodesConverter extends LazyLogging with FastaConverterBase with ExternalToolsUtils {

  def createBarcodeSet(fastaPath: Path,
                       contigs: Seq[ReferenceContig],
                       name: String,
                       outputDir: Path): BarcodeSet = {
    val faiIndex = handleCmdError(CallSamToolsIndex.run(fastaPath)) match {
      case Right(f) => f.toAbsolutePath
      case Left(err) => throw new Exception(s"samtools index failed: ${err.getMessage}")
    }
    val timeStamp = new SimpleDateFormat("yyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    def toTimeStampName(n: String) = s"${n}_$timeStamp"

    val nrecords = contigs.length
    val totalLength = contigs.foldLeft(0)((m, n) => m + n.length)
    
    // This is so clumsy
    val uuid = UUID.randomUUID()
    val createdAt = DatatypeFactory.newInstance().newXMLGregorianCalendar(new DateTime().toGregorianCalendar)
    val timeStampName = toTimeStampName("barcodeset")
    val fastaTimeStampName = toTimeStampName("fasta")
    
    val metatype = FileTypes.DS_BARCODE.fileTypeId
    val fastaMetaType = FileTypes.FASTA_REF.fileTypeId
    
    // Is this really not defined as a constant somewhere?
    
    val tags = "converted, reference"
    val description = s"Converted Reference $name"
    
    val metadata = new BarcodeSetMetadataType()
    metadata.setNumRecords(nrecords)
    metadata.setTotalLength(totalLength)

    val er = new ExternalResource()
    er.setCreatedAt(createdAt)
    er.setModifiedAt(createdAt)
    er.setMetaType(fastaMetaType)
    er.setName(s"Fasta $name")
    er.setUniqueId(UUID.randomUUID().toString)
    er.setTags(tags)
    er.setDescription("Converted with fasta-to-barcodes")
    er.setTimeStampedName(fastaTimeStampName)
    er.setResourceId(outputDir.relativize(fastaPath.toAbsolutePath).toString)

    val fai = new InputOutputDataType()
    fai.setUniqueId(UUID.randomUUID().toString)
    fai.setTimeStampedName(toTimeStampName("index"))
    fai.setResourceId(outputDir.relativize(faiIndex).toString)
    fai.setMetaType(FileTypes.I_SAM.fileTypeId)

    val fileIndices = new FileIndices()
    fileIndices.getFileIndex.add(fai)
    er.setFileIndices(fileIndices)

    val externalResources = new ExternalResources()
    externalResources.getExternalResource.add(er)

    val rs = new BarcodeSet()
    rs.setVersion(CommonConstants.DATASET_VERSION)
    rs.setMetaType(metatype)
    rs.setCreatedAt(createdAt)
    rs.setModifiedAt(createdAt)
    rs.setTimeStampedName(timeStampName)
    rs.setUniqueId(uuid.toString)
    rs.setName(name)
    rs.setDescription(description)
    rs.setTags(tags)
    rs.setDataSetMetadata(metadata)
    rs.setExternalResources(externalResources)
    rs
  }

  def createDataset(name: String, fastaPath: Path, outputDir: Path):
                   Either[DatasetConvertError, BarcodeSet] = {
    validateFastaFile(fastaPath) match {
      case Left(x) => Left(DatasetConvertError(s"${x}"))
      case Right(contigs) => Right(createBarcodeSet(fastaPath, contigs, name,
                                                    outputDir))
    }
  }

  def apply(name: String, fastaPath: Path, outputDir: Path,
            inPlace: Boolean = false, mkdir: Boolean = false):
            Either[DatasetConvertError, BarcodeSetIO] = {
    if (mkdir && (! Files.exists(outputDir))) outputDir.toFile.mkdir()
    val sanitizedName = ReposUtils.nameToFileName(name)
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
    val ofn = outputDir.resolve(s"${sanitizedName}/barcodeset.xml")
    createDataset(sanitizedName, fastaFinal, targetDir) match {
      case Right(rs) => {
        DataSetWriter.writeBarcodeSet(rs, ofn)
        Right(BarcodeSetIO(rs, ofn))
      }
      case Left(err) => Left(err)
    }
  }
}
