package com.pacbio.secondary.analysis.converters

import java.nio.file.{Path, Files}

import com.pacbio.secondary.analysis.converters.ReferenceInfoConverter._
import com.pacbio.secondary.analysis.datasets.io.DataSetWriter
import com.pacbio.secondary.analysis.datasets.{ReferenceDatasetFileIO, ReferenceDatasetIO, ReferenceSetIO}
import com.pacbio.secondary.analysis.externaltools.ExternalToolsUtils
import com.pacbio.secondary.analysis.legacy.{ReferenceEntry, ReferenceInfoUtils}
import com.pacbio.secondary.analysis.referenceUploader.{CmdCreate, ProcessHelper, ReposUtils}
import com.pacificbiosciences.pacbiodatasets.ReferenceSet
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils

import scala.util.{Failure, Success, Try}

/**
 * This leverages the RS-era `referenceUploader` to generate both the
 * 'strict' reference.info. XML and the reference DataSet XML.
 *
 *
 * Created by mkocher on 10/7/15.
 */
object FastaToReferenceConverter extends LazyLogging with ExternalToolsUtils {

  val VERSION = "0.4.0"
  val DEFAULT_PLOIDY = "unknown"
  val DEFAULT_ORGANISM = ""

  case class FastaConvertOptions(name: String, organism: Option[String], ploidy: Option[String], fastaPath: Path, outputDir: Path)

  private def toCmd(opts: FastaConvertOptions): CmdCreate = {

    // The old referenceUploader code will copy the file into a /{my-ref-name}/sequence/{file-name}.fasta
    val fastaFiles = Seq(opts.fastaPath.toAbsolutePath.toString)
    val rc = new CmdCreate()

    val baseSamToolsExe = which("samtools") getOrElse "samtools"
    val baseSawriterExe = which("sawriter") getOrElse "sawriter"

    logger.debug(s"found samtools exe -> $baseSamToolsExe")
    logger.debug(s"found sawriter exe -> $baseSawriterExe")

    val samToolExe = s"$baseSamToolsExe faidx"
    val saWriterExe = s"$baseSawriterExe -blt 8 -welter"

    rc.addPostCreationExecutable(ProcessHelper.Executable.SAW, saWriterExe)
    rc.addPostCreationExecutable(ProcessHelper.Executable.SamIdx, samToolExe)

    //rc.setRefType("sample")
    // Don't update the reference repo index.xml
    rc.skipUpdateIndexXml(true)
    rc.setDescription(s"Fasta converter v$VERSION to PacBio Reference.info.xml and ReferenceSet XML")
    rc.setFastaFiles(fastaFiles.toArray)
    rc.setName(opts.name)
    rc.setOrganism(opts.organism.getOrElse(DEFAULT_ORGANISM))
    rc.setPloidy(opts.ploidy.getOrElse(DEFAULT_PLOIDY))
    // This sets the root output directory. The referenceSet will be written to junk-output/{my-reference-name}
    rc.setReferenceRepos(opts.outputDir.toAbsolutePath.toString)
    rc
  }

  def apply(opts: FastaConvertOptions): Either[DatasetConvertError, ReferenceSetIO] = {

    PacBioFastaValidator(opts.fastaPath) match {
      case Some(x) => Left(DatasetConvertError(x.msg))
      case _ =>
        logger.info(s"passed PacBio Fasta pre-Validation ${opts.fastaPath.toAbsolutePath}")

        // The legacy code converts the name to 'id' ?
        val sanitizedName = ReposUtils.nameToFileName(opts.name)
        var referenceDir = opts.outputDir.resolve(sanitizedName)
        if (Files.exists(referenceDir)) return Left(DatasetConvertError(s"The directory ${referenceDir} already exists - please remove it or specify an alternate output directory or reference name."))

        val rc = toCmd(opts)
        println(s"Running Conversion '$rc'")
        rc.run()

        // The relative path to the reference.info.xml file (./my-ref/reference.info.xml)
        val xs = ReposUtils.getReferenceInfoXmlPath(sanitizedName)
        val referenceInfoXml = opts.outputDir.resolve(xs)

        // The reference will be written
        logger.debug(s"Loading from $referenceInfoXml and converting to ReferenceSet XML")
        val referenceInfo = ReferenceInfoUtils.loadFrom(referenceInfoXml)
        logger.info("Successfully Loaded ReferenceInfoXML")

        //val referenceSet = converter(referenceInfo, opts.name, Seq("converted", "fasta", "reference"))
        val referenceSet = convertReferenceInfoToDataSet(referenceInfo)

        val outputPath = opts.outputDir.resolve(s"$sanitizedName/referenceset.xml")

        logger.info(s"Writing Reference dataset xml to ${outputPath.toAbsolutePath} using v$VERSION")

        DataSetWriter.writeReferenceSet(referenceSet, outputPath)
        Right(ReferenceSetIO(referenceSet, outputPath))
    }
  }

  def apply(name: String, organism: Option[String], ploidy: Option[String], fastaPath: Path, outputDir: Path): Either[DatasetConvertError, ReferenceSetIO] = {
    val opts = FastaConvertOptions(name, organism, ploidy, fastaPath, outputDir)

    Try { apply(opts) } match {
      case Success(x) => x
      case Failure(ex) => Left(DatasetConvertError(ex.toString))
    }
  }
}
