package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.nio.file.{Paths, Path}

import com.pacbio.secondary.smrtlink.analysis.converters.IndexCreationError

/*
 * Call gmap_build to generate GMAP database files
 *
 */
object CallGmapBuild extends ExternalToolsUtils {

  val EXE = "gmap_build"

  def run(fastaPath: Path,
          outputDir: Path,
          gmapBuildExePath: String = EXE): Either[ExternalCmdFailure, Path] = {
    // the output directory will be $refName in $PWD

    val cmd = Seq(gmapBuildExePath,
                  "-D",
                  outputDir.toAbsolutePath.toString,
                  "-d",
                  "gmap_db",
                  fastaPath.toAbsolutePath.toString)

    // Write the output for debugging
    val cmdOut = outputDir.resolve("gmap.stdout")
    val cmdErr = outputDir.resolve("gmap.stderr")
    runCmd(cmd, cmdOut, cmdErr, cwd = Some(outputDir.toFile))
      .map(_ => outputDir.resolve("gmap_db"))
  }
}
