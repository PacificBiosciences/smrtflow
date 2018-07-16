package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.nio.file.{Files, Path, Paths}

import org.apache.commons.io.FileUtils

/*
 * Call ngmlr with dummy inputs to generate index files
 *
 */
object CallNgmlrIndex extends ExternalToolsUtils {

  val EXE = "ngmlr"
  private val SUFFICES = Seq("-enc.2.ngm", "-ht-13-2.2.ngm")

  def isAvailable(): Boolean = isExeAvailable(Seq(EXE, "--version"))

  /**
    * Will generate the NGMLR index files and delete any
    * temp files upon success (or failure)
    *
    * @param fastaPath    Input Fasta File
    * @param nproc        Number of procs to use
    * @param ngmlrExePath Abspath to NGMLR exe
    * @return
    */
  def run(
      outputDir: Path,
      fastaPath: Path,
      nproc: Int = 1,
      ngmlrExePath: String = EXE): Either[ExternalCmdFailure, Seq[Path]] = {

    val stdout = outputDir.resolve("ngmlr.stdout")
    val stderr = outputDir.resolve("ngmlr.stderr")

    val absPath = fastaPath.toAbsolutePath

    // By convention the tool will write to these files
    val indices = SUFFICES.map(s => Paths.get(s"${absPath}${s}"))

    val fastqTmp = Files.createTempFile("ngmlrInput", ".fastq")
    val bamTmp = Files.createTempFile("ngmlrOutput", ".bam")
    val tempFiles = Seq(fastqTmp, bamTmp)

    val cmd = Seq(ngmlrExePath,
                  "-r",
                  fastaPath.toString,
                  "-q",
                  fastqTmp.toString,
                  "-o",
                  bamTmp.toString,
                  "-t",
                  nproc.toString)

    val result = runUnixCmd(cmd, stdout, stderr, cwd = Some(outputDir.toFile))

    tempFiles.map(_.toFile).foreach(FileUtils.deleteQuietly)
    result.map(_ => indices)
  }
}
