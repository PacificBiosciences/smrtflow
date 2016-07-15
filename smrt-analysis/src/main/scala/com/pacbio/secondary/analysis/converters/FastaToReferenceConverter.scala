
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
import com.pacbio.secondary.analysis.externaltools.{CallSamToolsIndex, CallSaWriterIndex, ExternalCmdFailure, ExternalToolsUtils}
import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.secondary.analysis.datasets.io.DataSetWriter

import com.pacificbiosciences.pacbiobasedatamodel.IndexedDataType.FileIndices
import com.pacificbiosciences.pacbiodatasets.{ContigSetMetadataType, Contigs, ReferenceSet}
import com.pacificbiosciences.pacbiobasedatamodel.{ExternalResource, InputOutputDataType, ExternalResources}


object FastaToReferenceConverter extends FastaConverterBase[ReferenceSet, ContigSetMetadataType] with LazyLogging {

  protected val baseName: String = "reference"
  protected val dsName: String = "ReferenceSet"
  protected val programName: String = "fasta-to-reference"
  protected val metatype: String = FileTypes.DS_REFERENCE.fileTypeId

  override protected def createIndexFiles(fastaPath: Path): Seq[DatasetIndexFile] = {
    val faiIndex = CallSamToolsIndex.run(fastaPath) match {
      case Right(f) => f.toAbsolutePath
      case Left(err) => throw new Exception(s"samtools index failed: ${err.getMessage}")
    }
    val saIndex = CallSaWriterIndex.run(fastaPath) match {
      case Right(f) => f.toAbsolutePath
      case Left(err) => throw new Exception(s"sawriter failed: ${err.getMessage}")
    }
    Seq(
      DatasetIndexFile(FileTypes.I_SAM.fileTypeId, faiIndex.toAbsolutePath.toString),
      DatasetIndexFile(FileTypes.I_SAW.fileTypeId, saIndex.toAbsolutePath.toString))
  }

  override protected def setMetadata(ds: ReferenceSet, metadata: ContigSetMetadataType): Unit = ds.setDataSetMetadata(metadata)

  def createReferenceSet(fastaPath: Path,
                         refMetaData: ContigsMetaData,
                         name: String,
                         organism: Option[String],
                         ploidy: Option[String],
                         outputDir: Path): ReferenceSet = {
    val metadata = composeMetaData(refMetaData)
    organism match {
      case Some(o) => metadata.setOrganism(o)
      case _ => metadata.setOrganism("Unknown")
    }
    ploidy match {
      case Some(p) => metadata.setPloidy(p)
      case _ => metadata.setPloidy("Haploid")
    }
    composeDataSet(fastaPath, name, outputDir, metadata)
  }

  def createDataset(name: String, organism: Option[String],
                    ploidy: Option[String], fastaPath: Path, outputDir: Path):
                    Either[DatasetConvertError, ReferenceSet] = {
    PacBioFastaValidator(fastaPath) match {
      case Left(x) => Left(DatasetConvertError(s"${x}"))
      case Right(refMetaData) => Right(createReferenceSet(fastaPath, refMetaData,
                                                        name, organism, ploidy, outputDir))
    }
  }

  def apply(name: String, organism: Option[String], ploidy: Option[String],
            fastaPath: Path, outputDir: Path,
            inPlace: Boolean = false, mkdir: Boolean = false):
            Either[DatasetConvertError, ReferenceSetIO] = {
    val target = setupTargetDir(name, fastaPath, outputDir, inPlace, mkdir)
    createDataset(target.name, organism, ploidy, target.fastaPath,
                  target.dataDir) match {
      case Right(rs) => {
        DataSetWriter.writeReferenceSet(rs, target.dsFile)
        Right(ReferenceSetIO(rs, target.dsFile))
      }
      case Left(err) => Left(err)
    }
  }
}
