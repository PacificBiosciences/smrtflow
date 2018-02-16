package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.nio.file.{Paths, Path}

/**
  * External Call To sawriter (from blasr tools)
  *
  * Created by mkocher on 9/26/15.
  */
object CallSaWriterIndex extends ExternalToolsUtils {
  val EXE = "sawriter"

  def isAvailable(): Boolean = isExeAvailable(Seq(EXE, "--help"))

  def run(outputDir: Path,
          fastaPath: Path,
          exePath: String = EXE): Either[ExternalCmdFailure, Path] = {

    // Does sawriter let you set the output file path?
    val indexFile = fastaPath.toAbsolutePath.toString + ".sa"
    val indexPath = Paths.get(indexFile)

    val stdout = outputDir.resolve(s"$EXE.stdout")
    val stderr = outputDir.resolve(s"$EXE.stderr")

    val cmd = Seq(exePath,
                  indexPath.toAbsolutePath.toString,
                  fastaPath.toAbsolutePath.toString,
                  "-blt",
                  "8",
                  "-welter")

    runCmd(cmd, stdout, stderr, cwd = Some(outputDir.toFile)).map(_ =>
      indexPath)
  }
}
