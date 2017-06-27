import com.pacbio.secondary.analysis.externaltools.ExternalToolsUtils
import org.specs2.mutable.Specification


class PbServiceIntegrationSpec extends Specification{

  def toCmd(args: String*): Seq[String] = Seq("pbservice") ++ args
  // pack should be called and PATH should be exported before these
  // tests are called
  "pbservice cram test " should {
    "exe is found" in {
      ExternalToolsUtils.which("pbservice") must beSome
    }
    "help is working" in {
      ExternalToolsUtils.runSimpleCmd(toCmd("--help")) must beNone
    }
    "version is working" in {
      ExternalToolsUtils.runSimpleCmd(toCmd("--version")) must beNone
    }
    "get-status is working" in {
      ExternalToolsUtils.runSimpleCmd(toCmd("status")) must beNone
    }
  }

}
