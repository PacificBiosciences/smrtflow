import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, PbsmrtpipeConstants}
import org.specs2.mutable.Specification

class PbsmrtpipeEngineOptionsSpec extends Specification{

  sequential

  "Simple translating of XML opts to EngineOptions" should {
    "Simple pipeline option of nproc=7 dist=false" in {
      val opts = Seq(
        PipelineIntOption(PbsmrtpipeConstants.MAX_NPROC.id, "Max Nproc", 7, "desc"),
        PipelineBooleanOption(PbsmrtpipeConstants.DISTRIBUTED_MODE.id, "Distributed Mode", false, "desc")
      )

      val eopts = PbsmrtpipeEngineOptions(opts)
      eopts.maxNproc must beEqualTo(7)
      eopts.distributedMode must beEqualTo(false)
    }
  }

}
