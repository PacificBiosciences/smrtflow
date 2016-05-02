package com.pacbio.secondary.smrtserver

import com.pacbio.secondary.smrtlink.SmrtLinkConstants

/**
 *
 * Created by mkocher on 9/10/15.
 */
trait SmrtServerConstants extends SmrtLinkConstants {
  // Not completely sure about wrapping the logger service.
  // The motivation is that the sourceId can be set/modified here so that the jobId isn't leaked to
  // the tool
  final val LOG_PREFIX = "log"
  // passed to the pbsmrtpipe process to communicate back to the services to log events/updates
  final val LOG_PB_SMRTPIPE_RESOURCE_ID = "pbsmrtpipe"
}

object SmrtServerConstants extends SmrtServerConstants
