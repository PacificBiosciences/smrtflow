package com.pacbio.secondary.smrtlink.services

import com.pacbio.common.services.StatusCodeJoiners
import com.pacbio.secondary.smrtlink.JobServiceConstants

import spray.routing.Route

package object jobtypes {
  trait JobTypeServiceBase extends StatusCodeJoiners {
    val endpoint: String
    val description: String
    val routes: Route
  }

  abstract class JobTypeService
    extends JobTypeServiceBase
    with JobService
    with JobServiceConstants {
  }
}
