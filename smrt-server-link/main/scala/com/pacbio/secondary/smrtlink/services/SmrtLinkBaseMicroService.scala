package com.pacbio.secondary.smrtlink.services

import com.pacbio.common.services.PacBioService
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.typesafe.scalalogging.LazyLogging

/**
 * Base trait for SmrtLink-specific services. Adds a prefix to to all endpoints. See {{{SmrtLinkConstants}}}.
 */
trait SmrtLinkBaseMicroService extends
PacBioService with
SmrtLinkConstants with
LazyLogging {
  override def prefixedRoutes = pathPrefix(BASE_PREFIX) { super.prefixedRoutes }
}
