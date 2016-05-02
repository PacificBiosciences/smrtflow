package com.pacbio.secondary.smrtserver.services

import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.smrtlink.services.JobsBaseMicroService
import com.pacbio.secondary.smrtserver.loaders.ReportViewRulesResourceLoader
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.pacbio.secondary.smrtserver.models.SecondaryModels.ReportViewRule
import spray.httpx.SprayJsonSupport._

import scala.concurrent.duration._


/**
 *
 * Created by mkocher on 9/25/15.
 */
class ReportViewRulesService(ptvrs: Seq[ReportViewRule]) extends JobsBaseMicroService {

  import SecondaryAnalysisJsonProtocols._

  implicit val timeout = Timeout(4.seconds)
  val PTVR_PREFIX = "report-view-rules"

  val manifest = PacBioComponentManifest(toServiceId("secondary.report_view_rules"),
    "Report View Rules Service Service",
    "0.1.0",
    "Analysis Report View Rules Service", None)

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
