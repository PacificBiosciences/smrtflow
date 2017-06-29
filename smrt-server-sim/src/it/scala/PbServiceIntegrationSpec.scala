import java.nio.file.{Files, Path, Paths}

import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.analysis.externaltools.{ExternalCmdFailure, ExternalToolsUtils, PacBioTestData}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification


/**
  * This requires that `pbservice` is in the PATH prior to running
  *
  * This pattern should replace the cram-ish tests in siv-tests/bin/import_tests.sh
  * and this pattern should replace pbscala.t to centralize testing. This will
  * make it easier to understand the test coverage (XML is emitted by scala) and should
  * clarify gaps in test converage.
  *
  * I've added the a subset of the pbservice tests here that will work without adding
  * the minimal python requirements:
  * - pbcommand,pbsmrtpipe,pbcoretools,pbreports
  * - sawriter and ngmlr for FASTA to ReferenceSet
  */
class PbServiceIntegrationSpec extends Specification with ConfigLoader with LazyLogging{

  // Need to use the root dir to the data files
  private def getPacBioTestDataFilesJsonPath(): Path = {
    val px = conf.getString(PacBioTestData.PB_TEST_ID)
    Paths.get(px).toAbsolutePath
  }

  private def getPacBioDataTestDataDir() = getPacBioTestDataFilesJsonPath().getParent
  private def getByDataSetType(name: String) = getPacBioDataTestDataDir().resolve(name).toAbsolutePath

  def getSubreadSetsPath(): Path = getByDataSetType("SubreadSet")
  def getLambdaPath(): Path = getByDataSetType("ReferenceSet/lambdaNEB/referenceset.xml")

  def toCmd(args: String*): Seq[String] = Seq("pbservice") ++ args
  def runPbservice(args: String*): Option[ExternalCmdFailure] = ExternalToolsUtils.runSimpleCmd(toCmd(args:_*))

  "pbservice cram test " should {
    "load PacBioTestData " in {
      val p = getPacBioTestDataFilesJsonPath()
      logger.info(s"PacBio Test files.json data path $p")
      Files.exists(p) must beTrue
    }
    "pbservice exe is found in PATH" in {
      ExternalToolsUtils.which("pbservice") must beSome
    }
    "help is working" in {
      runPbservice("--help") must beNone
    }
    "version is working" in {
      runPbservice("--version") must beNone
    }
    "get-status is working" in {
      runPbservice("status") must beNone
    }
    "import-dataset SubreadSets by Dir" in {
      runPbservice("import-dataset", getSubreadSetsPath().toString) must beNone
    }
    "import-dataset ReferenceSet Lambda by XML" in {
      runPbservice("import-dataset", getLambdaPath().toString) must beNone
    }
    "import-dataset Recursively from Root Dir for All DataSet types" in {
      runPbservice("import-dataset", getPacBioDataTestDataDir().toString) must beNone
    }
    "get-jobs (default) type" in {
      runPbservice("get-jobs") must beNone
    }
    "get-jobs -t import-dataset --max-items" in {
      runPbservice("get-jobs", "-t", "import-dataset", "--max-items", "10") must beNone
    }
  }
}
