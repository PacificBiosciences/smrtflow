
package com.pacbio.secondary.smrtlink.analysis.jobs

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.zip._

import scala.util.Try

import org.apache.commons.io.{FileUtils,FilenameUtils}
import spray.json._

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetExporter

trait JobUtils
    extends DataSetExporter
    with DataSetFileUtils
    with SecondaryJobJsonProtocol {

  import JobModels._

  /**
   * Load a datastore JSON and convert all paths to relative
   * @param rootPath   base job directory to which all paths should be relative
   * @param dataStorePath   path to datastore JSON
   */
  def relativizeDataStore(rootPath: Path,
                          dataStorePath: Path): Path = {
    val ds = FileUtils.readFileToString(dataStorePath.toFile, "UTF-8")
                      .parseJson.convertTo[PacBioDataStore]
    val dsTmp = Files.createTempFile(s"relativized-datastore", ".json")
    val dss = ds.copy(files = ds.files.map { f =>
      f.copy(path = rootPath.relativize(Paths.get(f.path)).toString)
    }).toJson.toString
    FileUtils.writeStringToFile(dsTmp.toFile, dss, "UTF-8")
    dsTmp
  }

  case class JobExportSummary(nBytes: Int)

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
          writeDataSet(out, path, destPath, m.metatype, skipMissingFiles = true) //XXX
        }.getOrElse(exportFile(out, path, basePath))
      } else {
        exportFile(out, path, basePath)
      }
    } else if (f.isDirectory) {
      logger.info(s"Exporting subdirectory ${path.toString}...")
      f.listFiles.map(fn => exportPath(out, f.toPath, basePath)).sum
    } else {
      logger.warn(s"Skipping ${path.toString}")
      0
    }
  }

  /**
   * Package an entire job directory into a zipfile.
   * @param jobPath  path to job contents
   * @param zipFileName  path to output zip file
   */
  def exportJob(job: EngineJob,
                jobPath: Path,
                zipFileName: Path): Try[JobExportSummary] = Try {
    if (jobPath.toFile.isFile) {
      throw new RuntimeException(s"${jobPath.toString} is not a directory")
    }
    val out = newFile(zipFileName)
    var nBytes = exportPath(out, jobPath, jobPath)
    JobExportSummary(nBytes)
  }
}
