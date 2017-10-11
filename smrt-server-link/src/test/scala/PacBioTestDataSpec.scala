import java.nio.file.Files

import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

/*
 * Tests for interfacing with external PacBioTestData library - will be skipped
 * if library is not configured.
 */
class PacBioTestDataSpec extends Specification with LazyLogging {

  args(skipAll = !PacBioTestData.isAvailable)

  "PacBioTestData wrapper" should {
    "load files from JSON" in {
      PacBioTestData.isAvailable must beTrue
      val pbdata = PacBioTestData()
      val f = pbdata.getFile("lambda-fasta")
      println(f)
      Files.exists(f) must beTrue
    }
  }
}
