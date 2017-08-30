package com.pacbio.secondary.smrtlink.services

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.{PacBioComponentManifest, ReportViewRule}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.smrtlink.loaders.ReportViewRulesResourceLoader
import spray.httpx.SprayJsonSupport._


/**
 *
 * Created by mkocher on 9/25/15.
 */
class ReportViewRulesService(ptvrs: Seq[ReportViewRule]) extends SmrtLinkBaseRouteMicroService {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val PTVR_PREFIX = "report-view-rules"

  val manifest = PacBioComponentManifest(toServiceId("secondary.report_view_rules"),
    "Report View Rules Service Service",
    "0.1.0",
    "Analysis Report View Rules Service")

  val routes =
    pathPrefix(PTVR_PREFIX) {
      path("status") {
        get {
          complete {
            s"Hello. Loaded Report View rules ${ptvrs.length}"
          }
        }
      } ~
      pathEnd {
        get {
          complete {
            ptvrs
          }
        }
      } ~
      path(Segment) { sx =>
        get {
          complete {
            ok {
              ptvrs.find(_.id == sx)
                .getOrElse(throw new ResourceNotFoundError(s"Unable to find Report View Rule $sx"))
            }
          }
        }
      }
    }
}

trait ReportViewRulesResourceProvider {
  val reportViewRules = ReportViewRulesResourceLoader.loadResources
}


trait ReportViewRulesServiceProvider {
  this: ReportViewRulesResourceProvider
    with ServiceComposer =>

  val reportViewRulesService: Singleton[ReportViewRulesService] =
    Singleton(() => new ReportViewRulesService(reportViewRules))

  addService(reportViewRulesService)
}
