package com.pacbio.secondary.smrtlink.jobtypes

import java.net.URI
import java.nio.file.Path

import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  JobResultsWriter,
  MockJobUtils
}
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.models.BoundServiceEntryPoint
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

/**
  * Created by mkocher on 8/17/17.
  */
case class MockPbsmrtpipeJobOptions(
    name: Option[String],
    description: Option[String],
    pipelineId: String,
    entryPoints: Seq[BoundServiceEntryPoint],
    taskOptions: Seq[ServiceTaskOptionBase],
    workflowOptions: Seq[ServiceTaskOptionBase],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.MOCK_PBSMRTPIPE
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new MockPbsmrtpipeJob(this)
}

trait MockPbsmrtpipeUtils extends MockJobUtils {

  def runMockJob(job: JobResourceBase, resultsWriter: JobResultsWriter) = {
    //Ignore the entry points provided
    val entryPoints: Seq[BoundEntryPoint] = Seq.empty[BoundEntryPoint]
    val envPath: Option[Path] = None

    val resources = setupJobResourcesAndCreateDirs(job.path)
    val dsFiles = toMockDataStoreFiles(job.path)

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toSmrtLinkJobLog(logPath)

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
    writeEntryPoints(entryPoints, resources.entryPointsJson)

    logger.info(s"Completed running mock jobOptions in ${job.path.toString}")
    ds
  }
}

class MockPbsmrtpipeJob(opts: MockPbsmrtpipeJobOptions)
    extends ServiceCoreJob(opts)
    with MockPbsmrtpipeUtils {

  type Out = PacBioDataStore

  override def run(
      job: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    Right(runMockJob(job, resultsWriter))
  }
}
