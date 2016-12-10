
package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.io.File
import java.util.UUID

import org.apache.commons.io.{FileUtils,FilenameUtils}
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConversions._
import scala.collection.mutable

import com.pacbio.secondary.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.reports.ReportUtils
import com.pacbio.secondary.analysis.reports.ReportModels._
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacbio.secondary.analysis.datasets.io._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._

import com.pacificbiosciences.pacbiodatasets._
import com.pacificbiosciences.pacbiobasedatamodel.{InputOutputDataType,ExternalResources}


trait DeleteResourcesOptionsBase extends BaseJobOptions {}

case class DeleteResourcesOptions(path: Path, removeFiles: Boolean = true) extends BaseJobOptions with DeleteResourcesOptionsBase {
  def toJob = new DeleteResourcesJob(this)
}

case class DeleteDatasetsOptions(paths: Seq[Path], removeFiles: Boolean = true) extends BaseJobOptions with DeleteResourcesOptionsBase {
  def toJob = new DeleteDatasetsJob(this)
}

// internal result holder
case class DeletedFile(path: String, isDirectory: Boolean, nBytes: Long, wasDeleted: Boolean)


/*--- Base class for resource deletion (path-agnostic) ---*/

abstract class DeleteResourcesBase(opts: DeleteResourcesOptionsBase)
    extends BaseCoreJob(opts: DeleteResourcesOptionsBase)
    with MockJobUtils with timeUtils {
  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("delete_resources")
  val resourceType = "Unknown Path"

  protected def deleteFileOrDirectory(f: File, ignoreFailures: Boolean = true): DeletedFile = {
    val isDir = f.isDirectory
    val size = if (isDir) FileUtils.sizeOfDirectory(f) else FileUtils.sizeOf(f)
    val wasDeleted = Try {
      logger.info(s"Deleting ${f.toString} (${size} bytes, directory = ${isDir}")
      if (isDir) FileUtils.deleteDirectory(f)
      else f.delete
    } match {
      case Success(_) => true
      case Failure(err) => if (ignoreFailures) {
        logger.error(s"ERROR: ${err.getMessage}"); false
      } else throw err
    }
    DeletedFile(f.toString, isDir, size, wasDeleted)
  }

  protected def toReport(targetPaths: Seq[Path], deletedFiles: Seq[DeletedFile]): Report = {
    val nErrors = deletedFiles.count(_.wasDeleted == false).toLong
    val nBytesTotal = deletedFiles.map(_.nBytes).sum
    val tables = if (deletedFiles.nonEmpty) {
      val paths = ReportTableColumn("path", Some("Path"), deletedFiles.map(_.path))
      val directories = ReportTableColumn("is_dir", Some("Is Directory"), deletedFiles.map(f => if (f.isDirectory) "Y" else "N"))
      val nBytes = ReportTableColumn("n_bytes", Some("Deleted Bytes"), deletedFiles.map(_.nBytes))
      val wasDeleted = ReportTableColumn("was_deleted", Some("Delete Succeeded"), deletedFiles.map(f => if (f.wasDeleted) "Y" else "N"))
      List(
        ReportTable("deleted_files", Some("Deleted Paths"), Seq(
          paths, directories, nBytes, wasDeleted)))
    } else List()
    val attributes = mutable.ListBuffer(
        ReportStrAttribute("job_dir", "Directory", targetPaths.mkString(", ")),
        ReportLongAttribute("n_errors", "Number of Errors", nErrors),
        ReportLongAttribute("n_bytes", "Deleted Bytes", nBytesTotal))
    val nSkipped = deletedFiles.count(_.nBytes < 0).toLong
    if (nSkipped > 0) {
      attributes += ReportLongAttribute("n_skipped", "Skipped Or Missing Files", nSkipped)
    }
    Report(
      "smrtflow_delete_resources",
      "Delete Resources",
      attributes = attributes.toList,
      plotGroups = List.empty[ReportPlotGroup],
      tables = tables,
      uuid = UUID.randomUUID())
  }

  def runDelete(job: JobResourceBase, resultsWriter: JobResultWriter): Seq[DeletedFile]
  def makeReport(files: Seq[DeletedFile]): Report

  def run(job: JobResourceBase, resultsWriter: JobResultWriter):
            Either[ResultFailed, Out] = {
    val startedAt = JodaDateTime.now()
    //resultsWriter.writeLineStdout(s"Starting cleanup of ${opts.path} at ${startedAt.toString}")
    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toMasterDataStoreFile(logPath, "Log file of the details of the delete resources job")
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
          s"Report for ${resourceType} deletion")
        Right(PacBioDataStore(now, now, "0.2.1", Seq(logFile, rptFile)))
      case Failure(err) =>
        val runTimeSec = computeTimeDeltaFromNow(startedAt)
        Left(ResultFailed(job.jobId, jobTypeId.toString, s"Delete job ${job.jobId} of ${resourceType} failed with error '${err.getMessage}", runTimeSec, AnalysisJobStates.FAILED, host))
    }
  }
}


class DeleteResourcesJob(opts: DeleteResourcesOptions)
    extends DeleteResourcesBase(opts: DeleteResourcesOptions)
    with MockJobUtils with timeUtils {
  override val jobTypeId = JobTypeId("delete_job")
  override val resourceType = "Job"

  private def deleteDirFiles(targetDir: File, ignoreFailures: Boolean = true): Seq[DeletedFile] = {
    targetDir.listFiles.map { f =>
      val isDir = f.isDirectory
      val size = if (isDir) FileUtils.sizeOfDirectory(f) else FileUtils.sizeOf(f)
      val wasDeleted = Try {
        logger.info(s"Deleting ${f.toString} (${size} bytes, directory = ${isDir}")
        if (isDir) FileUtils.deleteDirectory(f)
        else f.delete
      } match {
        case Success(_) => true
        case Failure(err) => if (ignoreFailures) {
          logger.error(s"ERROR: ${err.getMessage}"); false
        } else throw err
      }
      DeletedFile(f.toString, isDir, size, wasDeleted)
    }
  }

  override def makeReport(files: Seq[DeletedFile]): Report =
    toReport(Seq(opts.path), files)

  override def runDelete(job: JobResourceBase, resultsWriter: JobResultWriter): Seq[DeletedFile] = {
    val startedAt = JodaDateTime.now()
    resultsWriter.writeLineStdout(s"Starting cleanup of ${opts.path} at ${startedAt.toString}")
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
      throw new Exception(s"The path '${jobDir.toString}' does not exist or is not a directory")
    }
  }
}


class DeleteDatasetsJob(opts: DeleteDatasetsOptions)
    extends DeleteResourcesBase(opts: DeleteDatasetsOptions)
    with MockJobUtils with timeUtils {
  override val jobTypeId = JobTypeId("delete_dataset")
  override val resourceType = "Dataset"
  private val BAM_RESOURCES = Seq(FileTypes.BAM_ALN, FileTypes.BAM_SUB, FileTypes.BAM_CCS, FileTypes.BAM_ALN, FileTypes.I_PBI, FileTypes.I_BAI, FileTypes.STS_XML, FileTypes.STS_H5).map(x => x.fileTypeId).toSet

  protected def deleteResource(path: Path): DeletedFile = {
    val f = path.toFile
    if (f.isDirectory) {
      throw new Exception(s"The path '${f.toString}' is a directory; only file resources may be deleted.")
    } else if (! f.exists) {
      logger.warn(s"File ${f.toString} not found")
      DeletedFile(f.toString, false, -1, false)
    } else deleteFileOrDirectory(f)
  }

  override def makeReport(files: Seq[DeletedFile]): Report =
    toReport(opts.paths, files)

  private def getPaths(dsType: DataSetMetaTypes.DataSetMetaType,
                       externalResources: ExternalResources): Seq[String] = {
    def isChildResource(e: InputOutputDataType): Boolean = {
      if (DataSetMetaTypes.BAM_DATASETS contains dsType) {
        val metaType = e.getMetaType
        val isChild = (BAM_RESOURCES contains metaType) || metaType.startsWith("PacBio.SubreadFile")
        if (!isChild) {
          logger.warn(s"Skipping file ${e.getResourceId} with meta-type $metaType")
        }
        isChild
      } else true // XXX are there any exceptions?  do we care about non-BAM?
    }
    Option(externalResources).map { ex =>
      ex.getExternalResource.filter(isChildResource).flatMap { e =>
        Seq(e.getResourceId) ++ getPaths(dsType, e.getExternalResources) ++
        Option(e.getFileIndices).map { fi =>
          fi.getFileIndex.flatMap { i => Seq(i.getResourceId) }
        }.getOrElse(Seq.empty[String])
      }
    }.getOrElse(Seq.empty[String])
  }

  override def runDelete(job: JobResourceBase, resultsWriter: JobResultWriter): Seq[DeletedFile] = {
    if (opts.paths.isEmpty) throw new Exception("No paths specified")
    val deletedFiles: Seq[DeletedFile] = opts.paths.map { dsPath =>
      if (! dsPath.toFile.isFile) {
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
        if (!opts.removeFiles) {
          logger.info("removeFiles=false, leaving files in place")
          Seq.empty[DeletedFile]
        } else {
          (for {
            r <- getPaths(dsType, ds.getExternalResources)
          } yield deleteResource(Paths.get(r))).toList ++
          Seq(deleteFileOrDirectory(dsPath.toFile))
        }
      }
    }.flatten
    /// XXX not sure what the most appropriate behavior here is...
    if ((deletedFiles.isEmpty || (deletedFiles.count(_.nBytes > 0) == 0)) &&
         opts.removeFiles) {
      throw new Exception("No files could be deleted - they may have already been removed from the filesystem")
    }
    deletedFiles
  }
}

// Utilities for setting up test datasets that can be safely deleted
object MockDataSetUtils {
  def makeBarcodedSubreads = {
    val pbdata = PacBioTestData()
    val targetDir = Files.createTempDirectory("dataset-contents")
    val subreadsDestDir = new File(targetDir.toString + "/SubreadSet")
    val barcodesDestDir = new File(targetDir.toString + "/BarcodeSet")
    val subreadsSrc = pbdata.getFile("barcoded-subreadset")
    val subreadsDir = subreadsSrc.getParent.toFile
    val barcodesSrc = pbdata.getFile("barcodeset")
    val barcodesDir = barcodesSrc.getParent.toFile
    // only copy the files we need for this SubreadSet, that way we can check
    // for an empty directory
    val prefix = FilenameUtils.getName(subreadsSrc.toString).replaceAll(".subreadset.xml$", "")
    for (f <- subreadsDir.listFiles) {
      val filename = FilenameUtils.getName(f.toString)
      if (filename.startsWith(prefix)) {
        val dest = new File(subreadsDestDir.toString + "/" + filename)
        FileUtils.copyFile(f, dest)
      }
    }
    FileUtils.copyDirectory(barcodesDir, barcodesDestDir)
    val subreads = Paths.get(subreadsDestDir.toString + "/" +
                             FilenameUtils.getName(subreadsSrc.toString))
    var barcodes = Paths.get(barcodesDestDir.toString + "/" +
                             FilenameUtils.getName(barcodesSrc.toString))
    val dsSubreads = DataSetLoader.loadSubreadSet(subreads)
    val dsBarcodes = DataSetLoader.loadBarcodeSet(barcodes)
    // set new UUIDs
    dsSubreads.setUniqueId(UUID.randomUUID().toString)
    dsBarcodes.setUniqueId(UUID.randomUUID().toString)
    DataSetWriter.writeSubreadSet(dsSubreads, subreads)
    DataSetWriter.writeBarcodeSet(dsBarcodes, barcodes)
    (subreads, barcodes)
  }
}
