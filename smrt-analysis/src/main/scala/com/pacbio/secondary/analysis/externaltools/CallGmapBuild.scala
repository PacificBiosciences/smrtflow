package com.pacbio.secondary.analysis.externaltools

import java.nio.file.{Paths, Path}

import com.pacbio.secondary.analysis.converters.IndexCreationError

/*
 * Call gmap_build to generate GMAP database files
 *
 */
object CallGmapBuild extends ExternalToolsUtils{

  val EXE = "gmap_build"
  lazy val CWD = Paths.get(".")

  def apply(fastaPath: Path, refName: String, outputDir: Path = CWD,
            gmapBuildExePath: String = EXE): Option[ExternalCmdFailure] = {
    val cmd = Seq(gmapBuildExePath, "-D", outputDir.toAbsolutePath.toString,
                  "-d", refName, fastaPath.toAbsolutePath.toString)
    runSimpleCmd(cmd)
  }

  def run(fastaPath: Path, refName: String, outputDir: Path = CWD,
          gmapBuildExePath: String = EXE): Either[ExternalCmdFailure, Path] = {
    // the output directory will be $refName in $PWD
    val dbPath = Paths.get(refName)
    apply(fastaPath, refName, outputDir, gmapBuildExePath) match {
      case Some(e) => Left(e)
      case _ => Right(dbPath)
    }
  }
}
