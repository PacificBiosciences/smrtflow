package com.pacbio.common.services

object BaseSmrtService {
  val BASE_PREFIX = "smrt-base"
}

/**
 * Base trait for services at the Base SMRT Server level.
 */
trait BaseSmrtService extends PacBioService {
  import BaseSmrtService._

  override def prefixedRoutes = pathPrefix(BASE_PREFIX) { super.prefixedRoutes }
}
