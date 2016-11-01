
package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.io.File
import java.util.UUID

import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success, Try}

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.reports.ReportUtils
import com.pacbio.secondary.analysis.reports.ReportModels._
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacbio.secondary.analysis.datasets.io._
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacificbiosciences.pacbiodatasets._


case class DeleteJobDirOptions(path: Path, removeDir: Boolean = true) extends BaseJobOptions {
  def toJob = new DeleteJobDirJob(this)
}

case class DeletedFile(path: String, isDirectory: Boolean, nBytes: Long, wasDeleted: Boolean)

class DeleteJobDirJob(opts: DeleteJobDirOptions)
    extends BaseCoreJob(opts: DeleteJobDirOptions)
    with MockJobUtils with timeUtils {
  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("delete_job")

  private def deleteFiles(targetDir: File, ignoreFailures: Boolean = true): Seq[DeletedFile] = {
    targetDir.listFiles.map { f =>
      val size = FileUtils.sizeOf(f)
      val wasDeleted = Try {
        logger.info(s"Deleting ${f.toString}")
        if (f.isDirectory) FileUtils.deleteDirectory(f)
        else f.delete
      } match {
        case Success(_) => true
        case Failure(err) => if (ignoreFailures) {
          logger.error(s"ERROR: ${err.getMessage}"); false
        } else throw err
      }
      DeletedFile(f.toString, f.isDirectory, size, wasDeleted)
    }
  }

  private def toReport(targetPath: Path, deletedFiles: Seq[DeletedFile]): Report = {
    val nErrors = deletedFiles.count(_.wasDeleted == false).toLong
    val nBytesTotal = deletedFiles.map(_.nBytes).sum
    val tables = if (deletedFiles.size > 0) {
      val paths = ReportTableColumn("path", Some("Path"), deletedFiles.map(_.path))
      val directories = ReportTableColumn("is_dir", Some("Is Directory"), deletedFiles.map(f => if (f.isDirectory) "Y" else "N"))
      val nBytes = ReportTableColumn("n_bytes", Some("Deleted Bytes"), deletedFiles.map(_.nBytes))
      val wasDeleted = ReportTableColumn("was_deleted", Some("Delete Succeeded"), deletedFiles.map(f => if (f.wasDeleted) "Y" else "N"))
      List(
        ReportTable("deleted_files", Some("Deleted Paths"), Seq(
          paths, directories, nBytes, wasDeleted)))
    } else List()
    Report(
      "delete_job",
      "Delete Job",
      attributes = List(
        ReportStrAttribute("job_dir", "Directory", targetPath.toString),
        ReportLongAttribute("n_errors", "Number of Errors", nErrors),
        ReportLongAttribute("n_bytes", "Deleted Bytes", nBytesTotal)),
      plotGroups = List[ReportPlotGroup](),
      tables = tables,
      uuid = UUID.randomUUID())
  }

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {
    val startedAt = JodaDateTime.now()
    resultsWriter.writeLineStdout(s"Starting cleanup of ${opts.path} at ${startedAt.toString}")
    val jobDir = opts.path.toFile
    var nFailures = 0
    Try {
      if (opts.removeDir) {
        deleteFiles(jobDir)
      } else {
        logger.info("removeDir=false, leaving files in place")
        Seq[DeletedFile]()
      }
    } match {
      case Success(files) =>
        val now = JodaDateTime.now()
        val r = toReport(opts.path, files)
        val reportPath = job.path.resolve(r.id + ".json")
        ReportUtils.writeReport(r, reportPath)
        val rptFile = DataStoreFile(
          r.uuid,
          "delete-job::delete-report",
          FileTypes.REPORT.fileTypeId.toString,
          reportPath.toFile.length(),
          now,
          now,
          reportPath.toAbsolutePath.toString,
          isChunked = false,
          "Job Delete Report",
          "Report for job directory deletion")
        val deleteFile = opts.path.resolve("DELETED").toFile
        val msg = if (opts.removeDir) {
          s"See ${reportPath.toString} for a report of deleted files"
        } else {
          s"This job has been deleted from the SMRT Link database, but all files have been left in place."
        }
        FileUtils.writeStringToFile(deleteFile, msg)
        Right(PacBioDataStore(now, now, "0.2.1", Seq(rptFile)))
      case Failure(err) =>
        val runTimeSec = computeTimeDeltaFromNow(startedAt)
        Left(ResultFailed(job.jobId, jobTypeId.toString, s"Delete job ${job.jobId} of directory ${opts.path.toString} failed with error '${err.getMessage}", runTimeSec, AnalysisJobStates.FAILED, host))
    }
  }
}
