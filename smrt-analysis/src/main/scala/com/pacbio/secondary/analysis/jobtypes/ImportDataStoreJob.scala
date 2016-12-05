package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.{Files, Paths}
import java.util.UUID
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.reports.ReportUtils
import spray.json._

import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.tools.timeUtils

import scala.util.{Success, Failure, Try}


case class ImportDataStoreOptions(path: String) extends BaseJobOptions {
  def toJob = new ImportDataStoreJob(this)
}

/**
 * Load and import datastore
 *
 * Todo
 * - convert to using XSD datamodel
 * - validate individual datastore files
 *
 * Created by mkocher on 6/16/15.
 */
class ImportDataStoreJob(opts: ImportDataStoreOptions) extends BaseCoreJob(opts: ImportDataStoreOptions)
with timeUtils
with MockJobUtils
with SecondaryJobJsonProtocol {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("import_datastore")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {
    val startedAt = JodaDateTime.now()

    logger.info(s"Trying to load datastore from ${opts.path}")

    val reportId = "smrtflow_import_datastore_report"

    val taskReport = ReportUtils.mockReport(reportId, "Import DataStore Report")
    val reportPath = job.path.resolve(reportId + ".json")
    ReportUtils.writeReport(taskReport, reportPath)

    val logPath = job.path.resolve(JobConstants.JOB_STDERR)
    val logFile = toMasterDataStoreFile(logPath)

    val reportDataStoreFile = DataStoreFile(
      taskReport.uuid,
      s"pbscala::$reportId",
      FileTypes.REPORT.fileTypeId.toString,
      reportPath.toFile.length(),
      startedAt,
      startedAt,
      reportPath.toAbsolutePath.toString,
      isChunked = false,
      "DataStore Import Report",
      "PacBio Report of the imported DataStore")

    val xs = Try {
      val path = Paths.get(opts.path)
      val xs = io.Source.fromFile(path.toFile)
      val xss = xs.getLines().mkString
      val jAst = xss.parseJson
      val ds = jAst.convertTo[PacBioDataStore]
      val localStorePath = job.path.resolve("datastore.json")
      // Should validate each file in the datastore
      val dsFiles = ds.files ++ Seq(reportDataStoreFile, logFile)
      val ds2 = PacBioDataStore(ds.createdAt, ds.updatedAt, ds.version, dsFiles)
      writeDataStore(ds2, localStorePath)
      ds2
    }

    xs match {
      case Success(x) => Right(x)
      case Failure(ex) => Left(ResultFailed(job.jobId, jobTypeId.id, s"Failed to import datastore ${ex.getMessage}", computeTimeDeltaFromNow(startedAt), AnalysisJobStates.FAILED, host))
    }
  }
}
