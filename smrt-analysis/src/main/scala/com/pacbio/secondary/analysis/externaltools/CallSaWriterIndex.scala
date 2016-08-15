package com.pacbio.secondary.analysis.externaltools

import java.nio.file.{Paths, Path}

import com.pacbio.secondary.analysis.converters.IndexCreationError

/**
 * External Call To sawriter (from blasr tools)
 * 
 * Created by mkocher on 9/26/15.
 */
object CallSaWriterIndex extends ExternalToolsUtils{
  val EXE = "sawriter"

  def isAvailable(): Boolean = {
    runCmd(Seq(EXE, "--help")).isRight
  }

  def apply(fastaPath: Path, exePath: String = EXE): Option[ExternalCmdFailure] = {
    val indexFile = Paths.get(s"${fastaPath.toAbsolutePath.toString}.sa")
    val cmd = Seq(exePath, indexFile.toAbsolutePath.toString, fastaPath.toAbsolutePath.toString, "-blt", "8", "-welter")
    runSimpleCmd(cmd)
  }

  def run(fastaPath: Path, samToolExePath: String = EXE): Either[ExternalCmdFailure, Path] = {
    // Does sawriter let you set the output file path?
    val indexFile = fastaPath.toAbsolutePath.toString + ".sa"
    val indexPath = Paths.get(indexFile)
    apply(fastaPath, EXE) match {
      case Some(e) => Left(e)
      case _ => Right(indexPath)
    }
  }
}
