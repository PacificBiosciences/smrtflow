import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import spray.httpx.SprayJsonSupport._


class SmrtLinkAnalysisJobExecutorSpec extends SmrtLinkAnalysisJobExecutorSpecBase {

  import SmrtLinkJsonProtocols._

  "Job Execution Status" should {
    "Sanity 'Example' Job Execution test" in {
      val url = toJobType("mock-pbsmrtpipe")
      Post(url, mockOpts) ~> totalRoutes ~> check {
        val msg = responseAs[EngineJob]
        logger.info(s"Response to $url -> $msg")
        status.isSuccess must beTrue
      }
    }
  }
}
