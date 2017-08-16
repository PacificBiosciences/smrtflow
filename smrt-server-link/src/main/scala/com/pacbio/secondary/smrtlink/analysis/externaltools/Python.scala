package com.pacbio.secondary.smrtlink.analysis.externaltools

trait Python extends ExternalToolsUtils {
  val EXE = "python"

  def hasModule(modName: String): Boolean = {
    runCmd(Seq(EXE, "-c", s"import ${modName}")).isRight
  }
}

object Python extends Python
