import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{EngineJob, JobTypeIds}
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import spray.httpx.SprayJsonSupport._


class SmrtLinkAnalysisJobExecutorSpec extends SmrtLinkAnalysisJobExecutorSpecBase {

  import SmrtLinkJsonProtocols._

  "Job Execution Status" should {
    "Sanity 'Example' Job Execution test" in {
      val url = toJobType(JobTypeIds.MOCK_PBSMRTPIPE.id)
      Post(url, mockOpts) ~> totalRoutes ~> check {
        val msg = responseAs[EngineJob]
        logger.info(s"Response to $url -> $msg")
        status.isSuccess must beTrue
      }
    }
    "Get a list of export-datasets job" in {
      Get(toJobType(JobTypeIds.EXPORT_DATASETS.id)) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
