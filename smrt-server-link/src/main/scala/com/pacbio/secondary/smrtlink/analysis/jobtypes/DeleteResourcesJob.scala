package com.pacbio.secondary.smrtlink.analysis.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.io.File
import java.util.UUID

import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels._
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID

import com.pacificbiosciences.pacbiodatasets._
import com.pacificbiosciences.pacbiobasedatamodel.{
  InputOutputDataType,
  ExternalResources
}

trait DeleteResourcesOptionsBase extends BaseJobOptions {}

case class DeleteResourcesOptions(path: Path,
                                  removeFiles: Boolean = true,
                                  override val projectId: Int =
                                    GENERAL_PROJECT_ID)
    extends BaseJobOptions
    with DeleteResourcesOptionsBase {
  def toJob = new DeleteResourcesJob(this)
}

case class DeleteDatasetsOptions(paths: Seq[Path],
                                 removeFiles: Boolean = true,
                                 override val projectId: Int =
                                   GENERAL_PROJECT_ID)
    extends BaseJobOptions
    with DeleteResourcesOptionsBase {
  def toJob = new DeleteDatasetsJob(this)
}

// internal result holder
case class DeletedFile(path: String,
                       isDirectory: Boolean,
                       nBytes: Long,
                       wasDeleted: Boolean)

/** Base trait for resource deletion (path-agnostic)
  *
  * This handles the mechanics of job setup, file deletion, and reporting.
  */
trait DeleteResourcesBase extends MockJobUtils with timeUtils {
  this: BaseCoreJob =>

  type Out = PacBioDataStore
  //val jobTypeId = JobTypeId("delete_resources")
  val resourceType = "Unknown Path"

  protected def deleteFileOrDirectory(
      f: File,
      ignoreFailures: Boolean = true): DeletedFile = {
    val isDir = f.isDirectory
    val size = if (isDir) FileUtils.sizeOfDirectory(f) else FileUtils.sizeOf(f)
    val wasDeleted = Try {
      logger.info(
        s"Deleting ${f.toString} (${size} bytes, directory = ${isDir}")
      if (isDir) FileUtils.deleteDirectory(f)
      else f.delete
    } match {
      case Success(_) => true
      case Failure(err) =>
        if (ignoreFailures) {
          logger.error(s"ERROR: ${err.getMessage}"); false
        } else throw err
    }
    DeletedFile(f.toString, isDir, size, wasDeleted)
  }

  protected def toReport(targetPaths: Seq[Path],
                         deletedFiles: Seq[DeletedFile]): Report = {
    val nErrors = deletedFiles.count(_.wasDeleted == false)
    val nBytesTotal = deletedFiles.map(_.nBytes).sum
    val tables = if (deletedFiles.nonEmpty) {
      val paths =
        ReportTableColumn("path", Some("Path"), deletedFiles.map(_.path))
      val directories = ReportTableColumn(
        "is_dir",
        Some("Is Directory"),
        deletedFiles.map(f => if (f.isDirectory) "Y" else "N"))
      val nBytes = ReportTableColumn("n_bytes",
                                     Some("Deleted Bytes"),
                                     deletedFiles.map(_.nBytes))
      val wasDeleted = ReportTableColumn(
        "was_deleted",
        Some("Delete Succeeded"),
        deletedFiles.map(f => if (f.wasDeleted) "Y" else "N"))
      List(
        ReportTable("deleted_files",
                    Some("Deleted Paths"),
                    Seq(paths, directories, nBytes, wasDeleted)))
    } else List()
    val attrs = Seq(
      ReportStrAttribute("job_dir", "Directory", targetPaths.mkString(", ")),
      ReportLongAttribute("n_errors", "Number of Errors", nErrors),
      ReportLongAttribute("n_bytes", "Deleted Bytes", nBytesTotal)
    )
    val nSkipped = deletedFiles.count(_.nBytes < 0)
    val reportAttributes =
      if (nSkipped == 0) attrs
      else {
        attrs ++ Seq(
          ReportLongAttribute("n_skipped",
                              "Skipped Or Missing Files",
                              nSkipped))
      }
    Report(
      "smrtflow_delete_resources",
      "Delete Resources",
      attributes = reportAttributes.toList,
      plotGroups = List.empty[ReportPlotGroup],
      tables = tables,
      uuid = UUID.randomUUID()
    )
  }

  def runDelete(job: JobResourceBase,
                resultsWriter: JobResultsWriter): Seq[DeletedFile]
  def makeReport(files: Seq[DeletedFile]): Report

  def run(job: JobResourceBase,
          resultsWriter: JobResultsWriter): Either[ResultFailed, Out] = {
    val startedAt = JodaDateTime.now()
    //resultsWriter.writeLine(s"Starting cleanup of ${opts.path} at ${startedAt.toString}")
    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toSmrtLinkJobLog(
      logPath,
      Some(
        s"${JobConstants.DATASTORE_FILE_MASTER_DESC} of the details of the delete resources job"))
    val reportPath = job.path.resolve("delete_report.json")

    Try {
      runDelete(job, resultsWriter)
    } match {
      case Success(files) =>
        val now = JodaDateTime.now()
        val r = makeReport(files)
        ReportUtils.writeReport(r, reportPath)
        val rptFile = DataStoreFile(
          r.uuid,
          s"${jobTypeId}::delete-report",
          FileTypes.REPORT.fileTypeId.toString,
          reportPath.toFile.length(),
          now,
          now,
          reportPath.toAbsolutePath.toString,
          isChunked = false,
          s"${jobTypeId} Delete Report",
          s"Report for ${resourceType} deletion"
        )
        Right(PacBioDataStore(now, now, "0.2.1", Seq(logFile, rptFile)))
      case Failure(err) =>
        val runTimeSec = computeTimeDeltaFromNow(startedAt)
        Left(
          ResultFailed(
            job.jobId,
            jobTypeId.toString,
            s"Delete job ${job.jobId} of ${resourceType} failed with error '${err.getMessage}",
            runTimeSec,
            AnalysisJobStates.FAILED,
            host
          ))
    }
  }
}

/** Job type for deleting the entire contents of a single directory.
  *
  */
class DeleteResourcesJob(opts: DeleteResourcesOptions)
    extends BaseCoreJob(opts: DeleteResourcesOptions)
    with DeleteResourcesBase
    with MockJobUtils
    with timeUtils {
  override val jobTypeId = JobTypeIds.DELETE_JOB
  override val resourceType = "Job"

  private def deleteDirFiles(
      targetDir: File,
      ignoreFailures: Boolean = true): Seq[DeletedFile] = {
    targetDir.listFiles.map(f => deleteFileOrDirectory(f, ignoreFailures))
  }

  override def makeReport(files: Seq[DeletedFile]): Report =
    toReport(Seq(opts.path), files)

  override def runDelete(job: JobResourceBase,
                         resultsWriter: JobResultsWriter): Seq[DeletedFile] = {
    val startedAt = JodaDateTime.now()
    resultsWriter.writeLine(
      s"Starting cleanup of ${opts.path} at ${startedAt.toString}")
    val reportPath = job.path.resolve("delete_report.json")
    val jobDir = opts.path.toFile

    if (jobDir.isDirectory) {
      val deleteFile = opts.path.resolve("DELETED").toFile
      val msg = if (opts.removeFiles) {
        s"See ${reportPath.toString} for a report of deleted files"
      } else {
        s"This job has been deleted from the SMRT Link database, but all files have been left in place."
      }
      if (opts.removeFiles) {
        val f = deleteDirFiles(jobDir)
        FileUtils.writeStringToFile(deleteFile, msg)
        f
      } else {
        logger.info("removeFiles=false, leaving files in place")
        FileUtils.writeStringToFile(deleteFile, msg)
        Seq.empty[DeletedFile]
      }
    } else {
      throw new Exception(
        s"The path '${jobDir.toString}' does not exist or is not a directory")
    }
  }
}

/** Job type for deleting a list of datasets, including external resources
  *
  * Currently this is intended to be used primarily for SubreadSets and other
  * BAM-based datasets; external resources subject to deletion are whitelisted
  * based on file type, which allows us to exclude any references or barcodes
  * (etc.) that may be shared with other datasets.
  */
class DeleteDatasetsJob(opts: DeleteDatasetsOptions)
    extends BaseCoreJob(opts: DeleteDatasetsOptions)
    with DeleteResourcesBase
    with MockJobUtils
    with timeUtils {
  override val jobTypeId = JobTypeIds.DELETE_DATASETS
  override val resourceType = "Dataset"
  private val DEFAULT_BAM_FILTER_METATYPES =
    FileTypes.BAM_RESOURCES.map(x => x.fileTypeId).toSet

  protected def deleteResource(path: Path): DeletedFile = {
    val f = path.toFile
    if (f.isDirectory) {
      throw new Exception(
        s"The path '${f.toString}' is a directory; only file resources may be deleted.")
    } else if (!f.exists) {
      logger.warn(s"File ${f.toString} not found")
      DeletedFile(f.toString, false, -1, false)
    } else deleteFileOrDirectory(f)
  }

  override def makeReport(files: Seq[DeletedFile]): Report =
    toReport(opts.paths, files)

  private def isBamResource(e: InputOutputDataType): Boolean = {
    val metaType = e.getMetaType
    val isChild = (DEFAULT_BAM_FILTER_METATYPES contains metaType)
    if (!isChild) {
      logger.warn(s"Skipping file ${e.getResourceId} with meta-type $metaType")
    }
    isChild
  }

  private def getPaths(
      dsType: DataSetMetaTypes.DataSetMetaType,
      externalResources: ExternalResources,
      filterResource: InputOutputDataType => Boolean): Seq[String] = {
    Option(externalResources)
      .map { ex =>
        ex.getExternalResource.asScala.filter(filterResource(_)).flatMap { e =>
          Seq(e.getResourceId) ++ getPaths(dsType,
                                           e.getExternalResources,
                                           filterResource) ++
            Option(e.getFileIndices)
              .map { fi =>
                fi.getFileIndex.asScala.flatMap { i =>
                  Seq(i.getResourceId)
                }
              }
              .getOrElse(Seq.empty[String])
        }
      }
      .getOrElse(Seq.empty[String])
  }

  override def runDelete(job: JobResourceBase,
                         resultsWriter: JobResultsWriter): Seq[DeletedFile] = {
    if (opts.paths.isEmpty) throw new Exception("No paths specified")
    val deletedFiles: Seq[DeletedFile] = opts.paths.flatMap { dsPath =>
      if (!dsPath.toFile.isFile) {
        logger.warn(s"${dsPath.toString} is missing, skipping")
        //Seq.empty[DeletedFile]
        Seq(DeletedFile(dsPath.toString, false, -1, false))
        //throw new Exception(s"${dsPath.toString} is not a file")
      } else {
        val basePath = dsPath.getParent
        val dsType = DataSetMetaTypes.fromPath(dsPath).get
        val ds = ImplicitDataSetLoader.loaderAndResolveType(dsType, dsPath)
        val dsId = UUID.fromString(ds.getUniqueId)
        val dsOutPath = s"${dsId}/${dsPath.getFileName.toString}"
        val dsTmp = Files.createTempFile(s"relativized-${dsId}", ".xml")
        def filterResource(e: InputOutputDataType): Boolean = {
          if (DataSetMetaTypes.BAM_DATASETS contains dsType) isBamResource(e)
          else true
        }
        if (!opts.removeFiles) {
          logger.info("removeFiles=false, leaving files in place")
          Seq.empty[DeletedFile]
        } else {
          getPaths(dsType, ds.getExternalResources, filterResource)
            .map(p => deleteResource(Paths.get(p)))
            .toList ++
            Seq(deleteFileOrDirectory(dsPath.toFile))
        }
      }
    }
    /// XXX not sure what the most appropriate behavior here is...
    if ((deletedFiles.isEmpty || (deletedFiles.count(_.nBytes > 0) == 0)) &&
        opts.removeFiles) {
      throw new Exception(
        "No files could be deleted - they may have already been removed from the filesystem")
    }
    deletedFiles
  }
}
