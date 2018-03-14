package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.nio.file.Path

/**
  * External Call To sawriter (from blasr tools)
  *
  * Created by mkocher on 9/26/15.
  */
object CallDataset extends ExternalToolsUtils {
  val EXE = "dataset"

  def isAvailable(): Boolean = isExeAvailable(Seq(EXE, "--help"))

  def runAbsolutize(
      xmlPath: Path,
      exePath: String = EXE): Either[ExternalCmdFailure, Path] = {

    val cmd =
      Seq(exePath, "absolutize", "--update", xmlPath.toAbsolutePath.toString)

    runCmd(cmd).map(_ => xmlPath)
  }
}
