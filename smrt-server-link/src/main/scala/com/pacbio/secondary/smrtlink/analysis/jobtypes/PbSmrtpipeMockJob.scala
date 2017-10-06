package com.pacbio.secondary.smrtlink.analysis.jobtypes

import java.io.{BufferedWriter, FileWriter}
import java.net.URL
import java.nio.file.{Files, Path}
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.testkit.MockFileUtils
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.Random

/**
  * Pure scala mock pbsmrtpipe layer that mocks out the required outputs of a pbsmrtpipe job useful for testing.
  *
  * The pipline id and entry points are not required to be strictly valid. The
  *
  * @param pipelineId
  * @param entryPoints
  * @param taskOptions
  * @param workflowOptions
  * @param envPath
  */
case class MockPbSmrtPipeJobOptions(
    pipelineId: String,
    entryPoints: Seq[BoundEntryPoint],
    taskOptions: Seq[ServiceTaskOptionBase],
    workflowOptions: Seq[ServiceTaskOptionBase],
    envPath: Option[Path] = None,
    override val projectId: Int = GENERAL_PROJECT_ID)
    extends BaseJobOptions {

  def toJob = new PbSmrtpipeMockJob(this)
}

// Put all the general utils for writing a mock pbsmrtpipe jobOptions, then refactor
// into real "jobOptions" level utils (e.g., progress updating, writing entry points, settings, options, etc...)
trait MockJobUtils extends LazyLogging with SecondaryJobJsonProtocol {

  def setupJobResourcesAndCreateDirs(outputDir: Path): AnalysisJobResources = {

    if (!Files.isDirectory(outputDir)) {
      logger.error(s"output dir is not a Dir ${outputDir.toString}")
    }

    def toPx(x: Path, name: String): Path = {
      val p = x.resolve(name)
      if (!Files.exists(p)) {
        logger.info(s"Creating dir $p")
        Files.createDirectories(p)
      }
      p
    }

    def toFx(x: Path, name: String): Path = {
      val p = x.resolve(name)
//      if (!Files.exists(p)) {
//        Files.createFile(p)
//      }
      p
    }

    val toP = toPx(outputDir, _: String)
    val toF = toFx(outputDir, _: String)

    // This is where the datastore.json will be written. Keep this
    val workflowPath = toP("workflow")

    // Don't create these for non-pbsmrtpipe jobs. This makes little sense to try to adhere to this interface
    val tasksPath = outputDir.resolve("tasks")
    val htmlPath = outputDir.resolve("html")
    val logPath = outputDir.resolve("logs")

    logger.debug(s"creating resources in ${outputDir.toAbsolutePath}")
    val r = AnalysisJobResources(
      outputDir,
      tasksPath,
      workflowPath,
      logPath,
      htmlPath,
      toFx(workflowPath, "datastore.json"),
      toFx(workflowPath, "entry-points.json"),
      toFx(workflowPath, "jobOptions-report.json")
    )

    logger.info(s"Successfully created resources")
    r
  }

  /**
    * Get the fundamental "log" for the job
    * FIXME(mpkocher)(2016-12-4) Centralizing this duplication. Should reevaluate the fundamental design
    *
    * @param path  Path to the Log file
    * @param description Custom description of the DataStore file
    * @return
    */
  def toMasterDataStoreFile(
      path: Path,
      description: String = s"Job Master Log"): DataStoreFile = {
    val now = JodaDateTime.now()
    DataStoreFile(
      UUID.randomUUID(),
      JobConstants.DATASTORE_FILE_MASTER_LOG_ID,
      FileTypes.LOG.fileTypeId,
      // probably wrong; the file isn't closed yet.  But it won't get
      // closed until after this method completes.
      path.toFile.length,
      now,
      now,
      path.toString,
      isChunked = false,
      "Job Master Log",
      description
    )
  }

  def toDatastore(jobResources: AnalysisJobResources,
                  files: Seq[DataStoreFile]): PacBioDataStore = {

    val version = "0.2.1"
    val createdAt = JodaDateTime.now()
    PacBioDataStore(createdAt, createdAt, version, files)
  }

  def writeStringToFile(s: String, path: Path): Path = {
    // for backward compatibility
    FileUtils.writeStringToFile(path.toFile, s)
    path
  }

  def writeDataStore(ds: PacBioDataStore, path: Path): Path = {
    FileUtils.writeStringToFile(path.toFile, ds.toJson.prettyPrint.toString)
    path
  }

  /**
    * This will a real fasta file that can be used
    *
    * @return
    */
  def toMockFastaDataStoreFile(rootDir: Path): DataStoreFile = {
    val createdAt = JodaDateTime.now()
    val uuid = UUID.randomUUID()
    val nrecords = 100
    val p = rootDir.resolve(s"mock-${uuid.toString}.fasta")
    MockFileUtils.writeMockFastaFile(nrecords, p)
    DataStoreFile(
      uuid,
      "mock-pbsmrtpipe",
      FileTypes.FASTA.fileTypeId,
      p.toFile.length(),
      createdAt,
      createdAt,
      p.toAbsolutePath.toString,
      isChunked = false,
      "Mock Fasta",
      s"Mock Fasta file generated with $nrecords records"
    )
  }

  def toMockDataStoreFiles(rootDir: Path): Seq[DataStoreFile] = {
    (0 until 4).map(x => toMockFastaDataStoreFile(rootDir))
  }

  def writeEntryPoints(entryPoints: Seq[BoundEntryPoint], path: Path): Path = {
    writeStringToFile(entryPoints.toJson.toString, path)
  }
}

class PbSmrtpipeMockJob(opts: MockPbSmrtPipeJobOptions)
    extends BaseCoreJob(opts: MockPbSmrtPipeJobOptions)
    with MockJobUtils {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeIds.MOCK_PBSMRTPIPE

  def run(job: JobResourceBase, resultsWriter: JobResultsWriter)
    : Either[ResultFailed, PacBioDataStore] = {

    val resources = setupJobResourcesAndCreateDirs(job.path)
    val dsFiles = toMockDataStoreFiles(job.path)

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile =
      toMasterDataStoreFile(logPath,
                            "Job Master log of the Import Dataset job")

    // This must follow the pbreport id format
    val reportId = "smrtflow_mock_job_report"

    val taskReport = ReportUtils.mockReport(reportId, "Mock smrtflow Report")
    val reportPath = job.path.resolve(reportId + ".json")
    ReportUtils.writeReport(taskReport, reportPath)
    val createdAt = JodaDateTime.now()

    val reportDataStoreFile = DataStoreFile(
      taskReport.uuid,
      "mock-pbsmrtpipe::mock-report",
      FileTypes.REPORT.fileTypeId.toString,
      reportPath.toFile.length(),
      createdAt,
      createdAt,
      reportPath.toAbsolutePath.toString,
      isChunked = false,
      "Mock Task Report",
      "Mock Task Report for mock pbsmrtpipe job type"
    )

    val dsFiles2 = dsFiles ++ Seq(reportDataStoreFile, logFile)

    val ds = toDatastore(resources, dsFiles2)
    writeDataStore(ds, resources.datastoreJson)
    val report = ReportUtils.toMockTaskReport("smrtflow_mock_pbsmrtpipe_job",
                                              "smrtflow Mock Pbsmrtpipe Job")
    ReportUtils.writeReport(report, resources.jobReportJson)
    writeEntryPoints(opts.entryPoints, resources.entryPointsJson)

    logger.info(s"Completed running mock jobOptions in ${job.path.toString}")
    Right(ds)
  }

}
