package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.nio.file.{Paths, Path}

/**
  * External Call To sawriter (from blasr tools)
  *
  * Created by mkocher on 9/26/15.
  */
object CallDataset extends ExternalToolsUtils {
  val EXE = "dataset"

  def isAvailable(): Boolean = isExeAvailable(Seq(EXE, "--help"))

  def absolutize(xmlPath: Path,
                 exePath: String = EXE): Option[ExternalCmdFailure] = {
    val cmd =
      Seq(exePath, "absolutize", "--update", xmlPath.toAbsolutePath.toString)
    runSimpleCmd(cmd)
  }

  def runAbsolutize(
      xmlPath: Path,
      exePath: String = EXE): Either[ExternalCmdFailure, Path] = {
    absolutize(xmlPath, exePath) match {
      case Some(e) => Left(e)
      case _ => Right(xmlPath)
    }
  }
}
