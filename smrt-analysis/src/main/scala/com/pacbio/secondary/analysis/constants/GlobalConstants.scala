package com.pacbio.secondary.analysis.constants

import com.pacbio.common.models.Constants
/**
 * Global Constants used by pbscala
 *
 * Created by mkocher on 9/26/15.
 */
object GlobalConstants {
  // Package Version
  val PB_SCALA_VERSION = Option(getClass.getPackage).map(_.getImplementationVersion).getOrElse("0.0.0-UNKNOWN-DEV")
}
