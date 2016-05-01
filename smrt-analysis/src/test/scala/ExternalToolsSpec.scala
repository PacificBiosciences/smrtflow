import com.pacbio.secondary.analysis.externaltools.ExternalToolsUtils
import org.specs2.mutable.Specification

class ExternalToolsSpec extends Specification with ExternalToolsUtils{

  sequential

  "Smoke test for external tools" should {
    "find ls in path" in {
      val path = which("ls")
      path must beSome
    }

  }

}
