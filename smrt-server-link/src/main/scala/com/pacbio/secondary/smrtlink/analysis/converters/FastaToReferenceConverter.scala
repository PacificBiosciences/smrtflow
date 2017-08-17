
package com.pacbio.secondary.smrtlink.analysis.converters

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

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets._
import com.pacbio.secondary.smrtlink.analysis.externaltools.{CallSaWriterIndex,CallNgmlrIndex}
import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetWriter

import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiodatasets.{ContigSetMetadataType, Contigs, ReferenceSet}
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, InputOutputDataType, ExternalResources}


object FastaToReferenceConverter extends FastaConverterBase[ReferenceSet, ContigSetMetadataType] with LazyLogging {

  protected val baseName: String = "reference"
  protected val dsName: String = "ReferenceSet"
  protected val programName: String = "fasta-to-reference"
  protected val metatype: String = FileTypes.DS_REFERENCE.fileTypeId
  protected val fastaMetatype: String = FileTypes.FASTA_REF.fileTypeId
  override protected val baseTags: Seq[String] = Seq("converted", "sv")

  protected def createIndexFiles(
      fastaPath: Path,
      skipNgmlr: Boolean = false): Seq[DatasetIndexFile] = {
    val faiIndex = createFaidx(fastaPath)
    val saIndex = CallSaWriterIndex.run(fastaPath) match {
      case Right(f) => f.toAbsolutePath
      case Left(err) => throw new Exception(s"sawriter failed: ${err.getMessage}")
    }
    val indices = Seq(
      DatasetIndexFile(FileTypes.I_SAM.fileTypeId, faiIndex),
      DatasetIndexFile(FileTypes.I_SAW.fileTypeId, saIndex.toAbsolutePath.toString))
    if (! skipNgmlr) {
      val ngmlrIndices = CallNgmlrIndex.run(fastaPath) match {
        case Right(paths) => paths
        case Left(err) => throw new Exception(s"ngmlr failed: ${err.getMessage}")
      }
      indices ++ Seq(
        DatasetIndexFile(FileTypes.I_NGMLR_ENC.fileTypeId, ngmlrIndices(0).toAbsolutePath.toString),
        DatasetIndexFile(FileTypes.I_NGMLR_TAB.fileTypeId, ngmlrIndices(1).toAbsolutePath.toString))
    } else indices
  }

  override protected def setMetadata(ds: ReferenceSet, metadata: ContigSetMetadataType): Unit = ds.setDataSetMetadata(metadata)

  def createReferenceSet(fastaPath: Path,
                         refMetaData: ContigsMetaData,
                         name: String,
                         organism: Option[String],
                         ploidy: Option[String],
                         outputDir: Path,
                         skipNgmlr: Boolean = false): ReferenceSet = {
    val metadata = composeMetaData(refMetaData)
    organism match {
      case Some(o) => metadata.setOrganism(o)
      case _ => metadata.setOrganism("Unknown")
    }
    ploidy match {
      case Some(p) => metadata.setPloidy(p)
      case _ => metadata.setPloidy("Haploid")
    }
    def makeIndices(f: Path) = createIndexFiles(f, skipNgmlr)
    composeDataSet(fastaPath, name, outputDir, metadata,
                   makeIndices = makeIndices)
  }

  def createDataset(name: String,
                    organism: Option[String],
                    ploidy: Option[String],
                    fastaPath: Path,
                    outputDir: Path,
                    skipNgmlr: Boolean = false):
                    Either[DatasetConvertError, ReferenceSet] = {
    PacBioFastaValidator(fastaPath) match {
      case Left(x) => Left(DatasetConvertError(s"${x}"))
      case Right(refMetaData) =>
        Right(createReferenceSet(fastaPath, refMetaData, name, organism,
                                 ploidy, outputDir, skipNgmlr))
    }
  }

  def apply(name: String, organism: Option[String], ploidy: Option[String],
            fastaPath: Path, outputDir: Path,
            inPlace: Boolean = false, mkdir: Boolean = false,
            skipNgmlr: Boolean = false):
            Either[DatasetConvertError, ReferenceSetIO] = {
    val target = setupTargetDir(name, fastaPath, outputDir, inPlace, mkdir)
    createDataset(target.name, organism, ploidy, target.fastaPath,
                  target.dataDir, skipNgmlr) match {
      case Right(rs) => {
        DataSetWriter.writeReferenceSet(rs, target.dsFile)
        Right(ReferenceSetIO(rs, target.dsFile))
      }
      case Left(err) => Left(err)
    }
  }
}
