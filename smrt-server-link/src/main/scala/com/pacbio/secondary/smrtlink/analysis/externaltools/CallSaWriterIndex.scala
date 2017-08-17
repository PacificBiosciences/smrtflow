package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.nio.file.{Paths, Path}

/**
 * External Call To sawriter (from blasr tools)
 * 
 * Created by mkocher on 9/26/15.
 */
object CallSaWriterIndex extends ExternalToolsUtils{
  val EXE = "sawriter"

  def isAvailable(): Boolean = isExeAvailable(Seq(EXE, "--help"))

  def apply(fastaPath: Path, exePath: String = EXE): Option[ExternalCmdFailure] = {
    val indexFile = Paths.get(s"${fastaPath.toAbsolutePath.toString}.sa")
    val cmd = Seq(exePath, indexFile.toAbsolutePath.toString, fastaPath.toAbsolutePath.toString, "-blt", "8", "-welter")
    runSimpleCmd(cmd)
  }

  def run(fastaPath: Path, exePath: String = EXE): Either[ExternalCmdFailure, Path] = {
    // Does sawriter let you set the output file path?
    val indexFile = fastaPath.toAbsolutePath.toString + ".sa"
    val indexPath = Paths.get(indexFile)
    apply(fastaPath, exePath) match {
      case Some(e) => Left(e)
      case _ => Right(indexPath)
    }
  }
}
