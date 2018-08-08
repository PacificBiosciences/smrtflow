package com.pacbio.secondary.smrtlink.analysis.converters

import java.nio.file.{Files, Path, Paths}
import java.io.{File, FileInputStream, FileOutputStream}
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.{UUID, Calendar}
import javax.xml.datatype.DatatypeFactory

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import spray.json._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets._
import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetWriter

import com.pacificbiosciences.pacbiodatasets.Contigs.Contig
import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiodatasets.{
  BarcodeSetMetadataType,
  Contigs,
  BarcodeSet
}
import com.pacificbiosciences.pacbiobasedatamodel.{
  ExternalResource,
  InputOutputDataType,
  ExternalResources
}

object FastaBarcodesConverter
    extends FastaConverterBase[BarcodeSet,
                               BarcodeSetMetadataType,
                               BarcodeSetIO]
    with LazyLogging {

  protected val baseName: String = "barcodes"
  protected val dsName: String = "BarcodeSet"
  protected val programName: String = "fasta-to-barcodes"
  protected val metatype: String = FileTypes.DS_BARCODE.fileTypeId
  protected val fastaMetatype: String = FileTypes.FASTA_BC.fileTypeId

  override protected def setMetadata(ds: BarcodeSet,
                                     metadata: BarcodeSetMetadataType): Unit =
    ds.setDataSetMetadata(metadata)

  def createBarcodeSet(fastaPath: Path,
                       refMetaData: ContigsMetaData,
                       name: String,
                       outputDir: Path): BarcodeSet = {
    val metadata = composeMetaData(refMetaData)
    composeDataSet(fastaPath, name, outputDir, metadata)
  }

  def createDataset(
      name: String,
      fastaPath: Path,
      outputDir: Path): Either[DatasetConvertError, BarcodeSet] = {
    PacBioFastaValidator(fastaPath) match {
      case Left(x) => Left(DatasetConvertError(s"${x}"))
      case Right(refMetaData) =>
        Right(createBarcodeSet(fastaPath, refMetaData, name, outputDir))
    }
  }

  def apply(
      name: String,
      fastaPath: Path,
      outputDir: Path,
      inPlace: Boolean = false,
      mkdir: Boolean = false): Either[DatasetConvertError, BarcodeSetIO] = {
    val target = setupTargetDir(name, fastaPath, outputDir, inPlace, mkdir)
    createDataset(target.name, target.fastaPath, target.dataDir) match {
      case Right(rs) => {
        DataSetWriter.writeBarcodeSet(rs, target.dsFile)
        Right(BarcodeSetIO(rs, target.dsFile))
      }
      case Left(err) => Left(err)
    }
  }
}
