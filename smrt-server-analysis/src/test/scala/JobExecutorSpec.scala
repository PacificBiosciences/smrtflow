import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import spray.httpx.SprayJsonSupport._


class JobExecutorSpec extends JobExecutorSpecBase {

  import SecondaryAnalysisJsonProtocols._

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
