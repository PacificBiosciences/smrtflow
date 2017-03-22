package com.pacbio.secondary.smrtlink.services

import com.pacbio.common.services.StatusCodeJoiners
import com.pacbio.secondary.smrtlink.models.ServiceDataSetMetadata
import com.pacbio.secondary.smrtlink.{SmrtLinkConstants, JobServiceConstants}

import spray.routing.Route

package object jobtypes {
  trait ProjectIdJoiner extends SmrtLinkConstants {
    def joinProjectIds(projectIds: Seq[Int]): Int = projectIds.distinct match {
      case ids if ids.size == 1 => ids.head
      case _ => GENERAL_PROJECT_ID
    }
  }

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
