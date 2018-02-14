package com.pacbio.secondary.smrtlink.io

import java.nio.file.{Files, Path, Paths}
import java.io.File
import java.util.UUID

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

import org.apache.commons.io.{FileUtils, FilenameUtils}

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels._
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacificbiosciences.pacbiodatasets._
import com.pacificbiosciences.pacbiobasedatamodel.{
  InputOutputDataType,
  ExternalResources
}

/** Base trait for resource deletion (path-agnostic)
  *
  * This handles the mechanics of file deletion, and reporting.
  * Kept separate from service job implementation to facilitate testing.
  */
trait DeleteResourcesUtils extends MockJobUtils with timeUtils {

  // internal result holder
  case class DeletedFile(path: String,
                         isDirectory: Boolean,
                         nBytes: Long,
                         wasDeleted: Boolean)

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

  protected def isBamResource(e: InputOutputDataType): Boolean = {
    val metaType = e.getMetaType
    val isChild = (DEFAULT_BAM_FILTER_METATYPES contains metaType)
    if (!isChild) {
      logger.warn(s"Skipping file ${e.getResourceId} with meta-type $metaType")
    }
    isChild
  }

  protected def getPaths(
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

  private def deleteDirFiles(
      targetDir: File,
      ignoreFailures: Boolean = true): Seq[DeletedFile] = {
    targetDir.listFiles.map(f => deleteFileOrDirectory(f, ignoreFailures))
  }

  protected def deleteJobDirFiles(jobDir: Path,
                                  removeFiles: Boolean,
                                  reportPath: Path): Report = {
    val deletedFiles = if (jobDir.toFile.isDirectory) {
      val deleteFile = jobDir.resolve("DELETED").toFile
      val msg = if (removeFiles) {
        s"See ${reportPath.toString} for a report of deleted files"
      } else {
        s"This job has been deleted from the SMRT Link database, but all files have been left in place."
      }
      if (removeFiles) {
        val f = deleteDirFiles(jobDir.toFile)
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
    toReport(Seq(jobDir), deletedFiles)
  }

  protected def deleteDataSetFiles(paths: Seq[Path],
                                   removeFiles: Boolean): Report = {
    val deletedFiles: Seq[DeletedFile] = paths.flatMap { dsPath =>
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
        if (!removeFiles) {
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
        removeFiles) {
      throw new Exception(
        "No files could be deleted - they may have already been removed from the filesystem")
    }
    toReport(paths, deletedFiles)
  }
}
