package com.pacbio.secondaryinternal.services

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.secondaryinternal.BaseInternalMicroService
import com.pacbio.secondaryinternal.Constants
import com.pacbio.secondaryinternal.InternalAnalysisJsonProcotols
import com.pacbio.secondaryinternal.JobResolvers
import com.pacbio.secondaryinternal.daos.{LimsDao, LimsDaoProvider, SmrtLinkResourceDao}
import com.pacbio.secondaryinternal.models.{InternalSubreadSet, SmrtLinkServerResource}
import com.pacbio.secondaryinternal.{BaseInternalMicroService, Constants, InternalAnalysisJsonProcotols, JobResolvers}
import spray.routing.PathMatchers.Segment

import scala.concurrent.ExecutionContext.Implicits.global
import spray._
import spray.routing._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import com.pacbio.common.services.ServiceComposer


/**
 * Service for SMRT Link Systems and resolving of job id -> SmrtLinkJob
 *
 * @param dao
 */
class LimsResolverService(dao: LimsDao) extends BaseInternalMicroService {

  import InternalAnalysisJsonProcotols._

  val manifest = PacBioComponentManifest(toServiceId("smrtlink_resource_resolver"),
    "SMRT Link Resource Resolver", "0.1.0",
    "Service to resolve job paths from job ids using SMRT Link Resources id.")

  val DEFAULT_MAX_RESULTS = 5000
  val PREFIX = "resolvers"

  // Maybe having the subreads resolving should be not under resolvers/*
  val subreadRoutes =
    pathPrefix(PREFIX / "subreads") {
      pathEndOrSingleSlash {
        get {
          complete {
            dao.getSubreadSets(DEFAULT_MAX_RESULTS).map(x => x.toList)
          }
        } ~
          post {
            entity(as[InternalSubreadSet]) { subreadSet =>
              complete {
                dao.addSubreadSet(subreadSet)
              }
            }
          }
      }
    }

      val expRoutes =
        path(PREFIX / "experiment" / IntNumber) { expId =>
          get {
            complete {
              println(s"Getting experiment $expId")
              dao.getByExpId(expId).map(x => x.toList)
            }
          }
        }

      val runcodeRoutes =
        path(PREFIX / "runcode" / "^[0-9]{7}-[0-9]{4}$".r) { runCode =>
          get {
            complete {
              dao.getByRunCode(runCode)
            }
          }
        }

    val routes = subreadRoutes ~ expRoutes ~ runcodeRoutes
}

trait LimsResolverServiceProvider {
  this: LimsDaoProvider with ServiceComposer =>

  final val limsResolverService: Singleton[LimsResolverService] =
    Singleton(() => new LimsResolverService(limsDao()))

  addService(limsResolverService)
}
