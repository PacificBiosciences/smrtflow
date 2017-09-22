
import java.nio.file.{Files,Path,Paths}
import java.util.UUID

import scala.collection.JavaConversions._

import org.apache.commons.io.{FileUtils, FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.datasets.MockDataSetUtils
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobModels, JobImportUtils, ExportJob}


trait MockJobExport {
  import JobModels._

  protected def setupFakeJob: EngineJob = {
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

  protected def setupFakeDataStore(job: EngineJob): PacBioDataStore = {
    val dsf = Seq(
      DataStoreFile(UUID.randomUUID(), "pbcommand.tasks.dev_mixed_app", FileTypes.LOG.fileTypeId, 1000, JodaDateTime.now(), JodaDateTime.now(), Paths.get(job.path).resolve("logs/master.log").toString, false, "Log file", "Log file"),
      DataStoreFile(UUID.randomUUID(), "pbcommand.tasks.dev_mixed_app", FileTypes.FASTA.fileTypeId, 1000, JodaDateTime.now(), JodaDateTime.now(), Paths.get(job.path).resolve("contigs.fasta").toString, false, "FASTA file", "FASTA file"))
    PacBioDataStore(JodaDateTime.now(), JodaDateTime.now(), "1.0", dsf)
  }
}


class JobUtilsSpec
    extends Specification
    with JobImportUtils
    with MockJobExport
    with LazyLogging {

  import JobModels._

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


  "JobExporter" should {
    "Export minimal fake job directory" in {
      val job = setupFakeJob
      val zipPath = Files.createTempFile("job", ".zip")
      val result = ExportJob(job, zipPath)
      result.toOption.get.nBytes must beGreaterThan(0L)
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
      result.toOption.get.nBytes must beGreaterThan(0L)
    }
    "Export with referenceset XML" in {
      val job = setupFakeJob
      val rs = setupDataSet(job)
      val zipPath = Files.createTempFile("job", ".zip")
      val result = ExportJob(job, zipPath)
      result.toOption.get.nBytes must beGreaterThan(0L)
    }
    "Export with entry point" in {
      val job = setupFakeJob
      val refPath = Paths.get(getClass.getResource(REF_PATH).getPath)
      val eps = Seq(BoundEntryPoint("eid_ref_dataset", refPath.toString))
      val zipPath = Files.createTempFile("job", ".zip")
      //val zipPath = Paths.get("job_eps.zip")
      val result = ExportJob(job, zipPath, eps)
      result.toOption.get.nBytes must beGreaterThan(0L)
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
      val refPath = Paths.get(getClass.getResource(REF_PATH).getPath)
      val eps = Seq(BoundEntryPoint("eid_ref_dataset", refPath.toString))
      val zipPath = Files.createTempFile("job", ".zip")
      //val zipPath = Paths.get("job_eps.zip")
      val result = ExportJob(job, zipPath, eps)
      result.toOption.get.nBytes must beGreaterThan(0L)
      // wipe the original directory, unpack and check files
      FileUtils.deleteDirectory(Paths.get(job.path).toFile)
      val unzipPath = Files.createTempDirectory("import-job")
      val result2 = expandJob(zipPath, unzipPath).toOption.get
      result2.nFiles === 16
      val manifest = getManifest(zipPath)
      val epsUnzip = manifest.entryPoints
      epsUnzip.forall(e => unzipPath.resolve(e.path).toFile.exists) === true
      val ds2Path = unzipPath.resolve("workflow/datastore.json")
      val ds2 = FileUtils.readFileToString(ds2Path.toFile, "UTF-8")
                         .parseJson.convertTo[PacBioDataStore]
      ds2.files.foreach { f =>
        val p = Paths.get(f.path)
        p.isAbsolute === true
        p.toFile.exists === true
      }
      val ref2Path = unzipPath.resolve("example.referenceset.xml")
      val ref2 = DataSetLoader.loadReferenceSet(ref2Path)
      DataSetValidator.validate(ref2, unzipPath)
      val resPaths2 = ref2.getExternalResources.getExternalResource.map(_.getResourceId)
      resPaths2.forall(Paths.get(_).isAbsolute) must beFalse
      // now absolutize paths and make sure they exist
      val ref3 = DataSetLoader.loadAndResolveReferenceSet(ref2Path)
      val resPaths3 = ref3.getExternalResources.getExternalResource.map(_.getResourceId)
      resPaths3.forall(Paths.get(_).toFile.exists) === true
      // and now the entry point dataset
      val ref4Path = unzipPath.resolve(epsUnzip(0).path)
      val ref4 = DataSetLoader.loadAndResolveReferenceSet(ref4Path)
      val resPaths4 = ref4.getExternalResources.getExternalResource.map(_.getResourceId)
      resPaths4.forall(Paths.get(_).toFile.exists) === true
    }
  }
  // TODO standalone expandJob test
}


class JobUtilsAdvancedSpec
    extends Specification
    with JobImportUtils
    with MockJobExport
    with LazyLogging {

  import JobModels._

  args(skipAll = !PacBioTestData.isAvailable)

  "Job Export using PacBioTestData" should {
    // this is somewhat redundant with JobUtilsSpec, but we want to check
    // SubreadSet export very carefully
    "Export job containing complete SubreadSet and BarcodeSet" in {
      val job = setupFakeJob
      val tasksDir = Paths.get(job.path).resolve("tasks")
      val fakeTaskDir = tasksDir.resolve("pbcommand.tasks.dev_mixed_app")
      fakeTaskDir.toFile.mkdirs
      val ds = setupFakeDataStore(job)
      val (subreads, barcodes) = MockDataSetUtils.makeBarcodedSubreads(Some(fakeTaskDir))
      val files2 = Seq(
        DataStoreFile(UUID.randomUUID(), "pbcommand.tasks.dev_mixed_app", FileTypes.DS_BARCODE.fileTypeId, 1000, JodaDateTime.now(), JodaDateTime.now(), barcodes.toString, false, "BarcodeSet XML", "BarcodeSet XML"),
        DataStoreFile(UUID.randomUUID(), "pbcommand.tasks.dev_mixed_app", FileTypes.DS_SUBREADS.fileTypeId, 1000, JodaDateTime.now(), JodaDateTime.now(), subreads.toString, false, "SubreadSet XML", "SubreadSet XML"))
      val workflowDir = Paths.get(job.path).resolve("workflow")
      workflowDir.toFile.mkdir
      val dsFile = workflowDir.resolve("datastore.json")
      val dsJson = ds.copy(files = ds.files ++ files2).toJson.prettyPrint
      FileUtils.writeStringToFile(dsFile.toFile, dsJson, "UTF-8")
      val refPath = PacBioTestData().getFile("lambdaNEB")
      val eps = Seq(BoundEntryPoint("eid_ref_dataset", refPath.toString))
      val zipPath = Files.createTempFile("job", ".zip")
      //val zipPath = Paths.get("job2.zip")
      val result = ExportJob(job, zipPath, eps)
      result.toOption.get.nBytes must beGreaterThan(0L)
      // wipe the original directory, unpack and check files
      FileUtils.deleteDirectory(Paths.get(job.path).toFile)
      val unzipPath = Files.createTempDirectory("import-job")
      val result2 = expandJob(zipPath, unzipPath).toOption.get
      result2.nFiles === 16
      val manifest = getManifest(zipPath)
      val epsUnzip = manifest.entryPoints
      epsUnzip.size === 1
      epsUnzip.forall(e => unzipPath.resolve(e.path).toFile.exists) === true
      val subreads2Path = unzipPath.resolve("tasks/pbcommand.tasks.dev_mixed_app/SubreadSet").resolve(FilenameUtils.getName(subreads.toString))
      val subreads2 = DataSetLoader.loadAndResolveSubreadSet(subreads2Path)
      DataSetValidator.validate(subreads2, unzipPath)
      val resPaths2 = subreads2.getExternalResources.getExternalResource.map(_.getResourceId)
      resPaths2.forall(Paths.get(_).toFile.exists) === true
    }
  }
}
