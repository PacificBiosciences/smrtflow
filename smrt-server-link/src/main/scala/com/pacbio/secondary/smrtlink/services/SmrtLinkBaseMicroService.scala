package com.pacbio.secondary.smrtlink.services

import scala.concurrent.duration._

import akka.util.Timeout

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
  implicit val timeout = Timeout(10.seconds)

  override def prefixedRoutes = pathPrefix(BASE_PREFIX) { super.prefixedRoutes }
}
