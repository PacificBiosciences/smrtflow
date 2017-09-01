
package com.pacbio.secondary.smrtlink.analysis.jobs

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.zip._

import scala.util.Try

import org.apache.commons.io.{FileUtils,FilenameUtils}
import spray.json._

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetExporter
import JobModels._


trait JobUtils
    extends DataSetExporter
    with DataSetFileUtils
    with SecondaryJobJsonProtocol {

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

  /**
   * Recursively export the contents of an arbitrary directory, relative to a
   * base path (defaults to the starting path)
   * @param out  open ZipOutputStream object
   * @param path  directory path to export
   * @param basePath  root path, archive paths will be relative to this
   */
  protected def exportPath(out: ZipOutputStream,
                           path: Path,
                           basePath: Path): Int = {
    val f = path.toFile
    if (f.isFile) {
      if (FilenameUtils.getName(path.toString) == "datastore.json") {
        val ds = relativizeDataStore(basePath, path)
        exportFile(out, path, basePath, Some(ds))
      } else if (path.toString.endsWith("set.xml")) {
        Try { getDataSetMiniMeta(path) }.toOption.map{ m =>
          val destPath = basePath.relativize(path)
          writeDataSet(out, path, destPath, m.metatype, skipMissingFiles = true)
        }.getOrElse(exportFile(out, path, basePath))
      } else {
        exportFile(out, path, basePath)
      }
    } else if (f.isDirectory) {
      logger.info(s"Exporting subdirectory ${path.toString}...")
      f.listFiles.map(fn => exportPath(out, fn.toPath, basePath)).sum
    } else {
      logger.warn(s"Skipping ${path.toString}")
      0
    }
  }

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

class JobExporter(job: EngineJob) extends JobUtils {

  case class JobExportSummary(nBytes: Int)

  /**
   * Package an entire job directory into a zipfile.
   * @param jobPath  path to job contents
   * @param zipFileName  path to output zip file
   */
  def toZip(zipFileName: Path): Try[JobExportSummary] = Try {
    haveFiles.clear
    val jobPath = Paths.get(job.path)
    if (jobPath.toFile.isFile) {
      throw new RuntimeException(s"${jobPath.toString} is not a directory")
    }
    val out = newZip(zipFileName)
    val manifest = Files.createTempFile("engine-job", ".json")
    FileUtils.writeStringToFile(manifest.toFile, job.toJson.prettyPrint, "UTF-8")
    var nBytes = exportPath(out, jobPath, jobPath) +
                 exportFile(out, jobPath.resolve("engine-job.json"), jobPath,
                            Some(manifest))
    out.close
    JobExportSummary(nBytes)
  }
}

object ExportJob {
  def apply(job: EngineJob, zipFileName: Path) = {
    new JobExporter(job).toZip(zipFileName)
  }
}
