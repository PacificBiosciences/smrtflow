import java.nio.file.{Paths, Path, Files}
import com.pacbio.secondary.analysis.externaltools.PbReports
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

/**
 * Tests for calling pbreports on an sts.xml file
 *
 * The tests will be skipped if the pbreports python modules aren't available
 */
class PbReportsSpec extends Specification with LazyLogging {

  args(skipAll = !PbReports.isAvailable())

  "pbreports wrapper" should {
    "create filter json" in {
      val name = "m54006_160113_202609.sts.xml"
      val stsXml = getClass.getResource(name)
      val tmpDir = Files.createTempDirectory("PbReportsStsXmlSpec")
      val outPath = Paths.get(tmpDir.toString(), "filter.json")
      PbReports.FilterStatsXml.run(Paths.get(stsXml.toURI()), outPath)
      Files.exists(outPath) must beTrue
    }
  }
}
