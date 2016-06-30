import akka.actor.{ActorRefFactory, ActorSystem}
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.analysis.jobtypes.SimpleDevJobOptions
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.{BoundServiceEntryPoint, PbSmrtPipeServiceOptions, ServiceTaskOptionBase}
import com.pacbio.secondary.smrtlink.services.jobtypes.MockPbsmrtpipeJobTypeProvider
import com.pacbio.secondary.smrtlink.services.{JobManagerServiceProvider, JobRunnerProvider}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.pacbio.secondary.smrtserver.services.jobtypes.SimpleServiceJobTypeProvider
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration

class JobExecutorSimpleSpec extends JobExecutorSpecBase {

  import SecondaryAnalysisJsonProtocols._

  "Job Simple Example " should {
    "Mock 'simple' job " in {
      val r = SimpleDevJobOptions(5, 6)
      val url = toJobType("simple")
      Post(url, r) ~> totalRoutes ~> check {
        val msg = responseAs[EngineJob]
        logger.info(s"Response to $url -> $msg")
        status.isSuccess must beTrue
      }
    }
  }
}
