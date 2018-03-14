package com.pacbio.secondary.smrtlink.analysis.externaltools

trait Python extends ExternalToolsUtils {
  val EXE = "python"

  def hasModule(modName: String): Boolean = {
    runCheckCall(Seq(EXE, "-c", s"import $modName")).isEmpty
  }
}

object Python extends Python
