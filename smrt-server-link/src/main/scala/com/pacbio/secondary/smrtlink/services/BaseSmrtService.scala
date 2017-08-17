package com.pacbio.secondary.smrtlink.services

object BaseSmrtService {
  val BASE_PREFIX = "smrt-base"
  // Attempt to collapse into single route prefix across the system
  val BASE_PREFIX_SL = "smrt-link"
}

/**
 * Base trait for services at the Base SMRT Server level.
 */
trait BaseSmrtService extends PacBioService {
  import BaseSmrtService._

  override def prefixedRoutes = pathPrefix(BASE_PREFIX) { super.prefixedRoutes } ~ pathPrefix(BASE_PREFIX_SL) { super.prefixedRoutes }
}
