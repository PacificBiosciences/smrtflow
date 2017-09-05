
package com.pacbio.secondary.smrtlink.analysis.jobs

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.zip._

import scala.util.Try

import org.apache.commons.io.{FileUtils,FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetExporter
import JobModels._


trait JobUtils extends SecondaryJobJsonProtocol {
  /**
   * Load a datastore JSON and convert all paths to relative, writing it to a
   * temporary file (or optional output path)
   * @param rootPath   base job directory to which all paths should be relative
   * @param dataStorePath   path to datastore JSON
   * @param dsOutPath  optional path to write to (default: new tmp file)
   */
  protected def relativizeDataStore(rootPath: Path,
                                    dataStorePath: Path,
                                    dsOutPath: Option[Path] = None): Path = {
    val ds = FileUtils.readFileToString(dataStorePath.toFile, "UTF-8")
                      .parseJson.convertTo[PacBioDataStore]
    val dss = ds.copy(files = ds.files.map { f =>
      f.copy(path = rootPath.relativize(Paths.get(f.path)).toString)
    }).toJson.toString
    val dsOut = dsOutPath.getOrElse(Files.createTempFile(s"datastore-relpaths", ".json"))
    FileUtils.writeStringToFile(dsOut.toFile, dss, "UTF-8")
    dsOut
  }

  /**
   * Load a datastore JSON and convert paths to absolute, writing it to a
   * temporary file (or optional output path)
   * @param rootPath   base job directory to which input paths are relative
   * @param dataStorePath   path to datastore JSON
   * @param dsOutPath  optional path to write to (default: new tmp file)
   */
  protected def absolutizeDataStore(rootPath: Path,
                                    dataStorePath: Path,
                                    dsOutPath: Option[Path] = None): Path = {
    val ds = FileUtils.readFileToString(dataStorePath.toFile, "UTF-8")
                      .parseJson.convertTo[PacBioDataStore]
    val dss = ds.copy(files = ds.files.map { f =>
      f.copy(path = rootPath.resolve(f.path).toString)
    }).toJson.toString
    val dsOut = dsOutPath.getOrElse(Files.createTempFile(s"datastore-abspaths", ".json"))
    FileUtils.writeStringToFile(dsOut.toFile, dss, "UTF-8")
    dsOut
  }
}


class JobExporter(job: EngineJob, zipPath: Path)
    extends DataSetExporter(zipPath)
    with DataSetFileUtils
    with JobUtils
    with SecondaryJobJsonProtocol
    with LazyLogging {

  case class JobExportSummary(nBytes: Long)

  /**
   * Recursively export the contents of an arbitrary directory, relative to a
   * base path (defaults to the starting path)
   * @param out  open ZipOutputStream object
   * @param path  directory path to export
   * @param basePath  root path, archive paths will be relative to this
   */
  protected def exportPath(path: Path,
                           basePath: Path): Long = {
    val f = path.toFile
    if (f.isFile) {
      if (FilenameUtils.getName(path.toString) == "datastore.json") {
        val ds = relativizeDataStore(basePath, path)
        exportFile(path, basePath, Some(ds))
      } else if (path.toString.endsWith("set.xml")) {
        Try { getDataSetMiniMeta(path) }.toOption.map{ m =>
          val destPath = basePath.relativize(path)
          if (haveFiles contains destPath.toString) {
            logger.warn(s"Skipping duplicate entry ${destPath.toString}"); 0
          } else {
            writeDataSet(path, destPath, m.metatype, Some(basePath), skipMissingFiles = true)
          }
        }.getOrElse(exportFile(path, basePath))
      } else {
        exportFile(path, basePath)
      }
    } else if (f.isDirectory) {
      logger.info(s"Exporting subdirectory ${path.toString}...")
      f.listFiles.map(fn => exportPath(fn.toPath, basePath)).sum
    } else {
      logger.warn(s"Skipping ${path.toString}"); 0
    }
  }

  /**
   * Package the entire job directory into a zipfile.
   */
  def toZip: Try[JobExportSummary] = {
    haveFiles.clear
    val jobPath = Paths.get(job.path)
    if (jobPath.toFile.isFile) {
      throw new RuntimeException(s"${jobPath.toString} is not a directory")
    }
    val manifest = Files.createTempFile("engine-job", ".json")
    FileUtils.writeStringToFile(manifest.toFile, job.toJson.prettyPrint, "UTF-8")
    var nBytes: Long = exportPath(jobPath, jobPath) +
                       exportFile(jobPath.resolve("engine-job.json"),
                                  jobPath, Some(manifest))
    out.close
    Try{JobExportSummary(nBytes)}
  }
}

object ExportJob {
  def apply(job: EngineJob, zipFileName: Path) = {
    new JobExporter(job, zipFileName).toZip
  }
}

trait JobImportUtils
    extends SecondaryJobJsonProtocol
    with JobUtils
    with LazyLogging {
  protected val BUFFER_SIZE = 2048

  case class JobImportSummary(nFiles: Int)

  /**
   * Decompress a zip file containing a job
   */
  def expandJob(zipFile: Path, jobPath: Path): Try[JobImportSummary] = Try {
    var zis = new ZipInputStream(new FileInputStream(zipFile.toFile));
    //get the zipped file list entry
    var ze = Option(zis.getNextEntry());
    var nFiles = 0
    while (ze.isDefined){
      val fileName = ze.get.getName();
      val newFile = jobPath.resolve(fileName).toFile;
      logger.debug(s"Deflating ${newFile.getAbsoluteFile}")
      Paths.get(newFile.getParent).toFile.mkdirs
      val fos = new FileOutputStream(newFile);
      var buffer = new Array[Byte](BUFFER_SIZE)
      var len = 0
      while ({len = zis.read(buffer); len > 0}) {
        fos.write(buffer, 0, len)
      }
      fos.close();
      if (FilenameUtils.getName(fileName) == "datastore.json") {
        logger.info(s"Updating paths in ${fileName}")
        absolutizeDataStore(jobPath, newFile.toPath, Some(newFile.toPath))
      }
      ze = Option(zis.getNextEntry())
      nFiles += 1
    }
    zis.closeEntry
    zis.close
    JobImportSummary(nFiles)
  }
}
