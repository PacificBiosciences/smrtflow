import java.nio.file.Files

import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestResourcesLoader
import com.pacbio.secondary.smrtlink.testkit.TestDataResourcesUtils
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

/*
 * Tests for interfacing with external PacBioTestData library - will be skipped
 * if library is not configured.
 */
class PacBioTestDataSpec
    extends Specification
    with LazyLogging
    with TestDataResourcesUtils {

  args(skipAll = !PacBioTestResourcesLoader.isAvailable)

  "PacBioTestData wrapper" should {
    "load files from JSON" in {
      PacBioTestResourcesLoader.isAvailable must beTrue
      val f = testResources.findById("lambda-fasta")
      f must beSome
      val isFound = f.map(x => Files.exists(x.path)).getOrElse(false)
      isFound must beTrue
    }
  }
}
