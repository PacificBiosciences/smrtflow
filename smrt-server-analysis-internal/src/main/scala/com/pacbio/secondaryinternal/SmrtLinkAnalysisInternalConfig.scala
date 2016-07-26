package com.pacbio.secondaryinternal

import java.nio.file.{Path, Paths}

import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.configloaders.ConfigLoader

/**
  * Created by mkocher on 7/21/16.
  */
trait SmrtLinkAnalysisInternalConfig extends ConfigLoader{

  lazy val reseqConditionsDir = Paths.get(conf.getString("smrt-server-analysis-internal.conditions-dir")).toAbsolutePath
}

// Trying to not infect the entire code base with Singletons
// This entire model needs to go away.
trait SmrtLinkAnalysisInternalConfigProvider extends ConfigLoader{

  val reseqConditions: Singleton[Path] =
    Singleton(() => Paths.get(conf.getString("smrt-server-analysis-internal.conditions-dir")).toAbsolutePath)
}
