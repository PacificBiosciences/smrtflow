package com.pacbio.secondary.analysis.externaltools

import java.nio.file.{Paths, Path}

import com.pacbio.secondary.analysis.converters.IndexCreationError

/**
 * External Call Sam Tools to create the fasta.fai Index file
 *
 * Created by mkocher on 9/26/15.
 */
object CallSamToolsIndex extends ExternalToolsUtils{

  val EXE = "samtools"

  def apply(fastaPath: Path, samToolExePath: String = EXE): Option[ExternalCmdFailure] = {
    val cmd = Seq(samToolExePath, "faidx", fastaPath.toAbsolutePath.toString)
    runSimpleCmd(cmd)
  }

  def run(fastaPath: Path, samToolExePath: String = EXE): Either[ExternalCmdFailure, Path] = {
    // The samtool doesn't allow you to set the explicit output file path
    val indexFile = fastaPath.toAbsolutePath.toString + ".fai"
    val indexPath = Paths.get(indexFile)
    apply(fastaPath, samToolExePath) match {
      case Some(e) => Left(e)
      case _ => Right(indexPath)
    }
  }
}
