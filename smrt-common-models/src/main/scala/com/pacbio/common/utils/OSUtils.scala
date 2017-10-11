package com.pacbio.common.utils

import java.nio.file.{Files, Paths}

import org.apache.commons.io.FileUtils
import org.apache.commons.lang.SystemUtils

/**
  * Created by mkocher on 7/14/17.
  */
trait OSUtils {
  // Is there a java lib that would better handle this?
  // Something similar to platform for python
  def getOsVersion(): String = {
    val procVersion = Paths.get("/proc/version")
    if (Files.exists(procVersion)) {
      FileUtils.readFileToString(procVersion.toFile).mkString
    } else {
      s"${SystemUtils.OS_NAME}; ${SystemUtils.OS_ARCH}; ${SystemUtils.OS_VERSION}"
    }
  }
}
