package com.pacbio.secondary.analysis.externaltools

trait Python extends ExternalToolsUtils {
  val EXE = "python"

  def hasModule(modName: String): Boolean = {
    runCmd(Seq(EXE, "-c", s"import ${modName}")).isRight
  }
}

object Python extends Python
