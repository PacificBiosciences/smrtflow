package com.pacbio.secondary.smrtlink.analysis.converters

import java.nio.file.Path

import com.typesafe.scalalogging.LazyLogging
import spray.json._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets._
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  CallNgmlrIndex,
  CallSaWriterIndex,
  ExternalCmdFailure
}
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetWriter
import com.pacificbiosciences.pacbiodatasets.{
  ContigSetMetadataType,
  ReferenceSet
}

import scala.util.Try

object FastaToReferenceConverter
    extends ReferenceConverterBase[ReferenceSet,
                                   ContigSetMetadataType,
                                   ReferenceSetIO]
    with LazyLogging {

  protected val baseName: String = "reference"
  protected val dsName: String = "ReferenceSet"
  protected val programName: String = "fasta-to-reference"
  protected val metatype: String = FileTypes.DS_REFERENCE.fileTypeId
  protected val fastaMetatype: String = FileTypes.FASTA_REF.fileTypeId
  override protected val baseTags: Seq[String] = Seq("converted", "sv")

  // Need to wrap this to enable composition
  private def createFaidxIndexFiles(
      fasta: Path): Either[ExternalCmdFailure, Seq[DatasetIndexFile]] = {
    val faiIndex = createFaidx(fasta)
    Right(Seq(DatasetIndexFile(FileTypes.I_SAM.fileTypeId, faiIndex)))
  }

  private def createSawriterIndexFiles(
      outputDir: Path,
      fasta: Path): Either[ExternalCmdFailure, Seq[DatasetIndexFile]] = {
    CallSaWriterIndex
      .run(outputDir, fasta)
      .map(
        f =>
          Seq(DatasetIndexFile(FileTypes.I_SAW.fileTypeId,
                               f.toAbsolutePath.toString)))
  }

  private def createNgmlrIndexFiles(
      outputDir: Path,
      fasta: Path): Either[ExternalCmdFailure, Seq[DatasetIndexFile]] = {
    // The underlying call should directly return these files to avoid index errors, or assumptions
    // about the ordering of the output
    CallNgmlrIndex.run(outputDir, fasta).map { ngmlrIndices =>
      Seq(
        DatasetIndexFile(FileTypes.I_NGMLR_ENC.fileTypeId,
                         ngmlrIndices(0).toAbsolutePath.toString),
        DatasetIndexFile(FileTypes.I_NGMLR_TAB.fileTypeId,
                         ngmlrIndices(1).toAbsolutePath.toString)
      )
    }
  }

  private def createIndexFiles(outputDir: Path,
                               fasta: Path,
                               skipNgmlr: Boolean = false)
    : Either[ExternalCmdFailure, Seq[DatasetIndexFile]] = {

    def runNgmlr(): Either[ExternalCmdFailure, Seq[DatasetIndexFile]] = {
      if (skipNgmlr) Right(Seq.empty[DatasetIndexFile])
      else createNgmlrIndexFiles(outputDir, fasta)
    }

    for {
      f1 <- createFaidxIndexFiles(fasta)
      f2 <- createSawriterIndexFiles(outputDir, fasta)
      f3 <- runNgmlr()
    } yield f1 ++ f2 ++ f3

  }

  override protected def setMetadata(ds: ReferenceSet,
                                     metadata: ContigSetMetadataType): Unit =
    ds.setDataSetMetadata(metadata)

  def createReferenceSet(fastaPath: Path,
                         refMetaData: ContigsMetaData,
                         name: String,
                         organism: Option[String],
                         ploidy: Option[String],
                         outputDir: Path,
                         skipNgmlr: Boolean = false): ReferenceSet = {

    val metadata = composeMetaData(refMetaData)

    metadata.setOrganism(organism.getOrElse("Unknown"))
    metadata.setPloidy(ploidy.getOrElse("Haploid"))

    def makeIndices(f: Path): Seq[DatasetIndexFile] = {
      createIndexFiles(outputDir, f, skipNgmlr) match {
        case Right(files) => files
        case Left(cmdFailure) =>
          // This is not really a great model
          throw new Exception(
            s"Failed to create index files in ${cmdFailure.runTime} sec. ${cmdFailure.getMessage}")
      }
    }

    composeDataSet(fastaPath,
                   name,
                   outputDir,
                   metadata,
                   makeIndices = makeIndices)
  }

  def createAndWriteReferenceSet(refMetaData: ContigsMetaData,
                                 name: String,
                                 organism: Option[String],
                                 ploidy: Option[String],
                                 fastaPath: Path,
                                 outputDir: Path,
                                 outputDataSetXml: Path,
                                 skipNgmlr: Boolean = false)
    : Either[DatasetConvertError, ReferenceSetIO] = {

    val tx = Try(
      createReferenceSet(fastaPath,
                         refMetaData,
                         name,
                         organism,
                         ploidy,
                         outputDir,
                         skipNgmlr)).map { rset =>
      DataSetWriter.writeReferenceSet(rset, outputDataSetXml)
      ReferenceSetIO(rset, outputDataSetXml)
    }

    tx.toEither.left.map(ex =>
      DatasetConvertError(s"Failed to create reference ${ex.getMessage}"))
  }

  def apply(name: String,
            organism: Option[String],
            ploidy: Option[String],
            fastaPath: Path,
            outputDir: Path,
            inPlace: Boolean = false,
            mkdir: Boolean = false,
            skipNgmlr: Boolean = false)
    : Either[DatasetConvertError, ReferenceSetIO] = {

    def toDsError(t: Throwable): DatasetConvertError =
      DatasetConvertError(s"${t.getMessage}")

    for {
      target <- Try(setupTargetDir(name, fastaPath, outputDir, inPlace, mkdir)).toEither.left
        .map(toDsError)
      contigMetaData <- PacBioFastaValidator(fastaPath).left.map(toDsError)
      rio <- createAndWriteReferenceSet(contigMetaData,
                                        target.name,
                                        organism,
                                        ploidy,
                                        target.fastaPath,
                                        target.dataDir,
                                        target.dsFile,
                                        skipNgmlr)
    } yield rio

  }

  def apply(name: String,
            organism: Option[String],
            ploidy: Option[String],
            fastaPath: Path,
            outputDir: Path,
            inPlace: Boolean,
            mkdir: Boolean) = {
    apply(name, organism, ploidy, fastaPath, outputDir, inPlace, mkdir, false)
  }
}
