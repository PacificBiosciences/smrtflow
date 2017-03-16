package com.pacbio.secondary.analysis.externaltools

import java.nio.file.{Files, Path, Paths}

import com.pacbio.secondary.analysis.converters.IndexCreationError

/*
 * Call ngmlr with dummy inputs to generate index files
 *
 */
object CallNgmlrIndex extends ExternalToolsUtils{

  val EXE = "ngmlr"
  val SUFFICES = Seq("-enc.2.ngm", "-ht-13-2.2.ngm")
  lazy val CWD = Paths.get(".")

  def isAvailable(): Boolean = isExeAvailable(Seq(EXE, "--help"))

  def apply(fastaPath: Path, nproc: Int = 1,
            ngmlrExePath: String = EXE): Option[ExternalCmdFailure] = {
    val fastqTmp = Files.createTempFile("ngmlrInput", ".fastq").toString
    val bamTmp = Files.createTempFile("ngmlrOutput", ".bam").toString
    val cmd = Seq(ngmlrExePath, "-r", fastaPath.toString, "-q", fastqTmp,
                  "-o", bamTmp, "-t", nproc.toString)
    runSimpleCmd(cmd)
  }

  def run(fastaPath: Path,
          nproc: Int = 1,
          ngmlrExePath: String = EXE): Either[ExternalCmdFailure, Seq[Path]] = {
    val baseDir = fastaPath.toFile.getParentFile.toPath
    val absPath = fastaPath.toAbsolutePath
    apply(fastaPath, nproc, ngmlrExePath) match {
      case Some(e) => Left(e)
      case _ => Right(SUFFICES.map(s => Paths.get(s"${absPath}${s}")))
    }
  }
}
