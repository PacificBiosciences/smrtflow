package com.pacbio.secondary.smrtlink.services

import scala.concurrent.duration._

import akka.util.Timeout

import com.pacbio.common.services.PacBioService
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.typesafe.scalalogging.LazyLogging

/**
 * Base trait for Jobs services. Adds a prefix to to all endpoints. See {{{JobServiceConstants}}}.
 */
trait JobsBaseMicroService extends PacBioService with JobServiceConstants with LazyLogging {
  implicit val timeout = Timeout(30.seconds)

  // Duplicate the Routes here to have backward compatibility
  override def prefixedRoutes = pathPrefix(ROOT_SERVICE_PREFIX) { super.prefixedRoutes } ~ pathPrefix(ROOT_SL_PREFIX) { super.prefixedRoutes }
}
