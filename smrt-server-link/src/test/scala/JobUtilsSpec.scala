
import java.nio.file.{Files,Path,Paths}
import java.util.UUID

import scala.collection.JavaConversions._

import org.apache.commons.io.FileUtils
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobModels, JobUtils, ExportJob}


class JobUtilsSpec extends Specification with JobUtils with LazyLogging {

  import JobModels._

  private def setupFakeJob: EngineJob = {
    val jobPath = Files.createTempDirectory("export-job")
    val fastaPath = jobPath.resolve("contigs.fasta")
    FileUtils.writeStringToFile(fastaPath.toFile, ">chr1\nacgtacgt", "UTF-8")
    val logDirPath = jobPath.resolve("logs")
    logDirPath.toFile.mkdir
    val logPath = logDirPath.resolve("master.log")
    FileUtils.writeStringToFile(logPath.toFile, "Hello world!", "UTF-8")
    EngineJob(1, UUID.randomUUID(), "My job", "Test job",
      JodaDateTime.now(), JodaDateTime.now(), AnalysisJobStates.SUCCESSFUL,
      "pbsmrtpipe", jobPath.toString, "{}", Some("smrtlinktest"), None,
      Some("4.0.0"), projectId = 10)
  }

  private def setupFakeDataStore(job: EngineJob): PacBioDataStore = {
    val dsf = Seq(
      DataStoreFile(UUID.randomUUID(), "pbcommand.tasks.dev_mixed_app", FileTypes.LOG.fileTypeId, 1000, JodaDateTime.now(), JodaDateTime.now(), Paths.get(job.path).resolve("logs").resolve("master.log").toString, false, "Log file", "Log file"),
      DataStoreFile(UUID.randomUUID(), "pbcommand.tasks.dev_mixed_app", FileTypes.FASTA.fileTypeId, 1000, JodaDateTime.now(), JodaDateTime.now(), Paths.get(job.path).resolve("contigs.fasta").toString, false, "FASTA file", "FASTA file"))
    PacBioDataStore(JodaDateTime.now(), JodaDateTime.now(), "1.0", dsf)
  }

  val REF_PATH = "/dataset-references/example_reference_dataset/reference.dataset.xml"
  val SEQ_PATH = "/dataset-references/example_reference_dataset/sequence"

  private def setupDataSet(job: EngineJob): DataStoreFile = {
    val refXml = Paths.get(getClass.getResource(REF_PATH).getPath).toFile
    val refPath = Paths.get(job.path).resolve("example.referenceset.xml")
    FileUtils.copyFile(refXml, refPath.toFile)
    val seqData = Paths.get(getClass.getResource(SEQ_PATH).getPath).toFile
    val seqPath = Paths.get(job.path).resolve("sequence")
    FileUtils.copyDirectory(seqData, seqPath.toFile)
    DataStoreFile(UUID.randomUUID(), "pbcommand.tasks.dev_mixed_app", FileTypes.DS_REFERENCE.fileTypeId, 1000, JodaDateTime.now(), JodaDateTime.now(), refPath.toString, false, "ReferenceSet XML", "ReferenceSet XML")
  }


  "Job Utils" should {
    "Export minimal fake job directory" in {
      val job = setupFakeJob
      val zipPath = Files.createTempFile("job", ".zip")
      val result = ExportJob(job, zipPath)
      result.toOption.get.nBytes must beGreaterThan(0)
    }
    "Export with datastore JSON" in {
      val job = setupFakeJob
      val ds = setupFakeDataStore(job)
      val workflowDir = Paths.get(job.path).resolve("workflow")
      workflowDir.toFile.mkdir
      val dsFile = workflowDir.resolve("datastore.json")
      FileUtils.writeStringToFile(dsFile.toFile, ds.toJson.prettyPrint, "UTF-8")
      val zipPath = Files.createTempFile("job", ".zip")
      val result = ExportJob(job, zipPath)
      result.toOption.get.nBytes must beGreaterThan(0)
    }
    "Export with referenceset XML" in {
      val job = setupFakeJob
      val rs = setupDataSet(job)
      val zipPath = Files.createTempFile("job", ".zip")
      val result = ExportJob(job, zipPath)
      result.toOption.get.nBytes must beGreaterThan(0)
    }
    "Combined with datastore and dataset XML, unzipped and validated" in {
      val job = setupFakeJob
      val rs = setupDataSet(job)
      val ds = setupFakeDataStore(job)
      val workflowDir = Paths.get(job.path).resolve("workflow")
      workflowDir.toFile.mkdir
      val dsFile = workflowDir.resolve("datastore.json")
      val dsJson = ds.copy(files = ds.files ++ Seq(rs)).toJson.prettyPrint
      FileUtils.writeStringToFile(dsFile.toFile, dsJson, "UTF-8")
      val zipPath = Files.createTempFile("job", ".zip")
      val result = ExportJob(job, zipPath)
      result.toOption.get.nBytes must beGreaterThan(0)
      // wipe the original directory, unpack and check files
      FileUtils.deleteDirectory(Paths.get(job.path).toFile)
      val unzipPath = Files.createTempDirectory("import-job")
      val result2 = expandJob(zipPath, unzipPath).toOption.get
      result2.nFiles === 10
      val ds2Path = unzipPath.resolve("workflow").resolve("datastore.json")
      val ds2 = FileUtils.readFileToString(ds2Path.toFile, "UTF-8")
                         .parseJson.convertTo[PacBioDataStore]
      ds2.files.foreach { f =>
        val p = Paths.get(f.path)
        p.isAbsolute === true
        p.toFile.exists === true
      }
      val ref2Path = unzipPath.resolve("example.referenceset.xml")
      val ref2 = DataSetLoader.loadReferenceSet(ref2Path)
      val resPaths2 = ref2.getExternalResources.getExternalResource.map(_.getResourceId)
      resPaths2.forall(Paths.get(_).isAbsolute) must beFalse
      // now absolutize paths and make sure they exist
      val ref3 = DataSetLoader.loadAndResolveReferenceSet(ref2Path)
      val resPaths3 = ref3.getExternalResources.getExternalResource.map(_.getResourceId)
      resPaths3.forall(Paths.get(_).toFile.exists) === true
    }
  }
}

/*
class JobUtilsAdvancedSpec extends Specification with LazyLogging {

  args(skipAll = !PacBioTestData.isAvailable)

  "Job Export" should {
    "Export job containing complete SubreadSet" in {
    }
  }
   
}*/
