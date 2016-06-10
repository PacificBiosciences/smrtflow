package com.pacbio.common.models

import java.util.Properties

/**
 * Created by mkocher on 10/13/15.
 */
trait Constants {
  final val DATASET_VERSION = "3.2.0"

  val SMRTFLOW_VERSION = {
    val files = getClass().getClassLoader().getResources("version.properties")
    if (files.hasMoreElements) {
      val in = files.nextElement().openStream()
      try {
        val prop = new Properties
        prop.load(in)
        prop.getProperty("version").replace("SNAPSHOT", "") + prop.getProperty("sha1").substring(0, 7)
      }
      finally {
        in.close()
      }
    }
    else {
      "unknown version"
    }
  }
}

object Constants extends Constants

