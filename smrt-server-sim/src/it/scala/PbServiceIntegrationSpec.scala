import java.nio.file.{Files, Path, Paths}

import com.pacbio.secondary.smrtlink.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.analysis.externaltools.{ExternalCmdFailure, ExternalToolsUtils, PacBioTestResourcesLoader}
import com.pacbio.secondary.smrtlink.testkit.{MockFileUtils, TestDataResourcesUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

/**
  * This requires that `pbservice` is in the PATH prior to running
  *
  * This pattern should replace the cram-ish tests in siv-tests/bin/import_tests.sh
  * and this pattern should replace pbscala.t to centralize testing. This will
  * make it easier to understand the test coverage (XML is emitted by scala) and should
  * clarify gaps in test coverage.
  *
  * Note, this also enables writing tests against the raw Client ServiceAccessLayer
  *
  * I've added the a subset of the pbservice tests here that will work without adding
  * the minimal python requirements:
  * - pbcommand,pbsmrtpipe,pbcoretools,pbreports
  * - sawriter and ngmlr for FASTA to ReferenceSet
  */
class PbServiceIntegrationSpec
    extends Specification
    with ConfigLoader
    with LazyLogging
      with TestDataResourcesUtils{

  args(skipAll = !PacBioTestResourcesLoader.isAvailable)

  // NOTE, these test must be run serially to avoid import dataset collisions
  // Or make each test uniquely import dataset types
  sequential

  // Get the root directory of a dataset for testing importing by dir
  private def getRootDirOfTestDataSet(ix: String): Path =
    testResources.findById(ix).get.path.getParent

  def getSubreadSetsPath(): Path = testResources.findById("subreads-sequel").get.path.getParent
  def getLambdaPath(): Path = testResources.findById("lambdaNEB").get.path

  val DEEP_DEBUG = true

  def toCmd(args: String*): Seq[String] = {
    val dx =
      if (DEEP_DEBUG) Seq("--log2stdout", "--debug") else Seq.empty[String]
    Seq("pbservice") ++ args ++ dx
  }

  def runPbservice(args: String*): Option[ExternalCmdFailure] = {
    logger.info(s"Running pbservice command $args")
    val rx = ExternalToolsUtils.runCheckCall(toCmd(args: _*))
    rx
  }

  "pbservice cram test " should {
    "pbservice exe is found in PATH" in {
      ExternalToolsUtils.which("pbservice") must beSome
    }
    "help is working" in {
      runPbservice("--help") must beNone
    }
    "version is working" in {
      runPbservice("--version") must beNone
    }
    "raise non-valid subparser" in {
      runPbservice("not-an-valid-option") must beSome
    }
    "get-status is working" in {
      runPbservice("status") must beNone
    }
    "import-dataset SubreadSets by Dir" in {
      runPbservice("import-dataset", getSubreadSetsPath().toString) must beNone
    }
    "import-dataset HdfSubreadSets by Dir" in {
      runPbservice("import-dataset", getRootDirOfTestDataSet("hdfsubreads").toString) must beNone
    }
    "import-dataset BarcodeSet by Dir" in {
      runPbservice("import-dataset", getRootDirOfTestDataSet("barcodeset").toString) must beNone
    }
    "import-dataset AlignmentSet by Dir" in {
      runPbservice("import-dataset", getRootDirOfTestDataSet("aligned-xml").toString) must beNone
    }
    "import-dataset ConsensusAlignmentSet by Dir" in {
      runPbservice(
        "import-dataset",
        getRootDirOfTestDataSet("rsii-ccs-aligned").toString) must beNone
    }
    "import-dataset ConsensusReadSet by Dir" in {
      runPbservice("import-dataset",
                   getRootDirOfTestDataSet("ccs-barcoded").toString) must beNone
    }
    "import-dataset ContigSet by Dir" in {
      runPbservice("import-dataset", getRootDirOfTestDataSet("contigset").toString) must beNone
    }
    "import-dataset ReferenceSet Lambda by XML" in {
      runPbservice("import-dataset", getLambdaPath().toString) must beNone
    }
    "get-jobs (default) type" in {
      runPbservice("get-jobs") must beNone
    }
    "get-jobs -t import-dataset --max-items" in {
      runPbservice("get-jobs", "-t", "import-dataset", "--max-items", "10") must beNone
    }
    "get-datasets (default) type" in {
      runPbservice("get-datasets") must beNone
    }
    "get-datasets by subreads type" in {
      runPbservice("get-datasets", "-t", "subreads") must beNone
    }
    "get-datasets by references types" in {
      runPbservice("get-datasets", "--dataset-type", "references") must beNone
    }
    "get-manifests" in {
      runPbservice("get-manifests") must beNone
    }
    "get-bundles" in {
      runPbservice("get-bundles") must beNone
    }
    "get-jobs" in {
      runPbservice("get-jobs") must beNone
    }
    "get-jobs -t import-dataset and get-job 1" in {
      runPbservice("get-jobs", "-t", "import-dataset") must beNone
      // There must be atleast 1 job in the system by now
      runPbservice("get-job", "1") must beNone
    }
    "get-jobs -t merge-datasets --job-state SUCCESSFUL --max-items 10" in {
      runPbservice("get-jobs",
                   "--job-type",
                   "merge-datasets",
                   "--job-state",
                   "SUCCESSFUL",
                   "--max-items",
                   "10") must beNone
    }
    "get-alarms" in {
      runPbservice("get-alarms") must beNone
    }
    "show-pipelines" in {
      runPbservice("show-pipelines") must beNone
    }
    "import-barcodes" in {
      val fastaPath = MockFileUtils.writeMockTmpFastaFile()
      runPbservice("import-barcodes",
                   fastaPath.toAbsolutePath.toString,
                   "MyBarcodes") must beNone
    }
    // This requires sawriter
    //    "import-fasta" in {
    //      val fastaPath:Path = MockFileUtils.writeMockTmpFastaFile()
    //      runPbservice("import-fasta", fastaPath.toAbsolutePath.toString, "--name", "MyRef", "--organism", "MyOrg", "--ploidy", "haploid", "--log2stdout")
    //    }
  }
}
