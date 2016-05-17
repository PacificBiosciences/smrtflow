package com.pacbio.secondaryinternal.services

import com.pacbio.common.dependency.Singleton

import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.secondaryinternal.daos.{SmrtLinkResourceDao, SmrtLinkResourceDaoProvider}
import com.pacbio.secondaryinternal.models.SmrtLinkServerResource
import com.pacbio.secondaryinternal.tempbase.ServiceComposer
import com.pacbio.secondaryinternal.{Constants, BaseInternalMicroService, JobResolvers, InternalAnalysisJsonProcotols}

import scala.concurrent.ExecutionContext.Implicits.global

import spray._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


/**
  * Service for SMRT Link Systems and resolving of job id -> SmrtLinkJob
  *
  * @param dao
  */
class SmrtLinkResourceService(dao: SmrtLinkResourceDao) extends BaseInternalMicroService {

  import InternalAnalysisJsonProcotols._

  val manifest = PacBioComponentManifest(toServiceId("smrtlink_resource_resolver"),
    "SMRT Link Resource Resolver", "0.1.0",
    "Service to resolve job paths from job ids using SMRT Link Resources id.", None)

  val PREFIX = "smrtlink-systems"

  val routes =
    pathPrefix(PREFIX) {
      pathEndOrSingleSlash {
        get {
          complete {
            // MK. Why do I have to call toList for the serialization to work?
            dao.getServers.map(x => x.toList)
          }
        } ~
          post {
            entity(as[SmrtLinkServerResource]) { smrtLinkSystem =>
              complete {
                dao.addServer(smrtLinkSystem)
              }
            }
          }
      } ~
        path(Segment) { systemId =>
          get {
            complete {
              dao.getServerById(systemId)
            }
          }
        } ~
        path(Segment / "job" / IntNumber) { (systemId, jobId) =>
          get {
            complete {
              dao.getJobById(systemId, jobId)
            }
          }
        }
    }
}

trait SmrtLinkResourceServiceProvider {
  this: SmrtLinkResourceDaoProvider with ServiceComposer =>

  final val smrtLinkResourceService: Singleton[SmrtLinkResourceService] =
    Singleton(() => new SmrtLinkResourceService(smrtLinkResourceDao()))

  addService(smrtLinkResourceService)
}
