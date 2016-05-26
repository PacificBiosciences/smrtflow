package com.pacbio.secondary.lims.services

import com.pacbio.common.services.PacBioService

/**
  * Shared base class for all custom web services related to LIMS
  */
trait LimsService extends PacBioService {
  // namespace for derived services and URL prefix
  val baseServiceName = "smrt-lims"
  // prefix all URLs with this namespace
  override def prefixedRoutes =
    pathPrefix(separateOnSlashes(baseServiceName)) {
      super.prefixedRoutes
    }
}
