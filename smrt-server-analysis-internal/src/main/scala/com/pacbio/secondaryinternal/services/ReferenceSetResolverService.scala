package com.pacbio.secondaryinternal.services

import com.pacbio.common.dependency.Singleton

import com.pacbio.common.models.PacBioComponentManifest

import com.pacbio.secondaryinternal.BaseInternalMicroService
import com.pacbio.secondaryinternal.Constants
import com.pacbio.secondaryinternal.InternalAnalysisJsonProcotols
import com.pacbio.secondaryinternal.JobResolvers
import com.pacbio.secondaryinternal.daos.{ReferenceResourceDao, ReferenceResourceDaoProvider}
import com.pacbio.secondaryinternal.models.ReferenceSetResource
import com.pacbio.secondaryinternal.tempbase.ServiceComposer
import com.pacbio.secondaryinternal.{Constants, BaseInternalMicroService, JobResolvers, InternalAnalysisJsonProcotols}

import spray.routing.PathMatchers.Segment

import scala.concurrent.ExecutionContext.Implicits.global

import spray._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


/**
  * Service for SMRT Link Systems and resolving of job id -> SmrtLinkJob
  *
  * @param dao
  */
class ReferenceSetResolverService(dao: ReferenceResourceDao) extends BaseInternalMicroService {

  import InternalAnalysisJsonProcotols._

  val manifest = PacBioComponentManifest(toServiceId("reference_resolver"),
    "SMRT Link Reference Resolver", "0.1.0",
    "Service to Globally resolve ReferenceSets by 'id', such as 'lambdaNEB'", None)

  val DEFAULT_MAX_RESULTS = 5000
  val PREFIX = "resolvers"

  val routes =
    pathPrefix(PREFIX / "references") {
      pathEndOrSingleSlash {
        get {
          complete {
            dao.getResources(DEFAULT_MAX_RESULTS).map(x => x.toList)
          }
        } ~
          post {
            entity(as[ReferenceSetResource]) { referenceSetResource =>
              complete {
                dao.addResourceById(referenceSetResource)
              }
            }
          }
      } ~
      path(Segment) { id =>
        get {
          complete {
            dao.getResourceById(id)
          }
        }
      }
    }
}

trait ReferenceSetResolverServiceProvider {
  this: ReferenceResourceDaoProvider with ServiceComposer =>

  final val referenceSetResolverService: Singleton[ReferenceSetResolverService] =
    Singleton(() => new ReferenceSetResolverService(referenceResourceDao()))

  addService(referenceSetResolverService)
}
