
package com.pacbio.secondary.smrtlink.analysis.datasets.io

import java.nio.file.{Files, Path, Paths}
import java.io._
import java.net.URI
import java.util.UUID
import java.util.zip._

import scala.collection.mutable

import com.typesafe.scalalogging.LazyLogging
import collection.JavaConversions._
import collection.JavaConverters._

import com.pacbio.secondary.smrtlink.analysis.datasets._
import com.pacificbiosciences.pacbiobasedatamodel.InputOutputDataType
import com.pacificbiosciences.pacbiodatasets.DataSetType

trait ExportBase extends LazyLogging {
  // XXX note that because of this mutable tracking variable, this trait should
  // always be used as a mixin for self-contained classes
  protected val haveFiles = mutable.Set.empty[String]
  protected val BUFFER_SIZE = 2048

  def newZip(zipPath: Path): ZipOutputStream = {
    haveFiles.clear
    val dest = new FileOutputStream(zipPath.toFile)
    new ZipOutputStream(new BufferedOutputStream(dest))
  }

  /**
   * Low-level call for writing the contents of a file to an open zipfile
   * @param out  open ZipOutputStream object
   * @param path  actual path to input file
   * @param zipOutPut  path to write to the zipfile
   */
  protected def writeFile(out: ZipOutputStream,
                          path: Path,
                          zipOutPath: String): Int = {
    val ze = new ZipEntry(zipOutPath)
    out.putNextEntry(ze)
    val input = new FileInputStream(path.toString)
    val data = new Array[Byte](BUFFER_SIZE)
    var nRead = -1
    var nWritten = 0
    while ({nRead = input.read(data); nRead > 0}) {
      out.write(data, 0, nRead)
      nWritten += nRead
    }
    nWritten
  }

  /**
   * Wrapper for writeFile that guards against redundancy, relativizes the
   * path, and optionally reads contents from an alternate path
   * @param out  open ZipOutputStream object
   * @param path  canonical path to exported file
   * @param basePath  root directory to export from; paths in zipfile will be
   *                  relative to this
   * @param srcPath  optional path to read from, e.g. if we wrote a temporary
   *                 copy with modifications
   */
  protected def exportFile(out: ZipOutputStream,
                           path: Path,
                           basePath: Path,
                           srcPath: Option[Path] = None): Int = {
    val destPath = basePath.relativize(path).toString
    if (haveFiles contains destPath) {
      logger.info(s"Skipping duplicate file ${destPath}"); 0
    } else {
      logger.info(s"Writing file ${destPath} to zip")
      haveFiles += destPath
      writeFile(out, srcPath.getOrElse(path), destPath)
    }
  }

  /**
   * Given a resource path of unknown form, a base path for the file(s)
   * referencing this resource, and a root destination in the zip file,
   * determine the appropriate relative resource path, and the path to write
   * to the zip file.  This allows us to cope with cases where a dataset and
   * its resources live in different directories under the directory being
   * exported.
   * @param resource  resourceId field from a DataSet, or similar
   * @param basePath  directory from which this resource is being reference (i.e. directory of the current dataset)
   * @param destPath  base destination path in the zip file
   * @param archiveRoot  optional root directory for the entire export
   */
  protected def relativizeResourcePath(
      resource: Path,
      basePath: Path,
      destPath: Path,
      archiveRoot: Option[Path]): (Path, Path) = {
    if (resource.isAbsolute) {
      if (resource.startsWith(basePath)) {
        // if the resourceId is an absolute path, but a subdirectory of the
        // base path, we convert it to the corresponding relative path first
        val finalPath = basePath.relativize(resource)
        (finalPath, destPath.resolve(finalPath))
      } else if (archiveRoot.isDefined && resource.startsWith(archiveRoot.get)) {
        (basePath.relativize(resource), archiveRoot.get.relativize(resource).normalize())
      } else {
        val finalPath = Paths.get(s".${resource}")
        (finalPath, destPath.resolve(finalPath).normalize())
      }
    } else {
      // FIXME what if the resource is relative and outside basePath?
      (resource, destPath.resolve(resource).normalize())
    }
  }
}

trait DataSetExporter extends ExportBase with LazyLogging {

  protected def writeResourceFile(out: ZipOutputStream,
                                  destPath: Path,
                                  res: InputOutputDataType,
                                  basePath: Path,
                                  archiveRootPath: Option[Path],
                                  ignoreMissing: Boolean = false): Int = {
    val rid = res.getResourceId
    val uri = URI.create(rid.replaceAll(" ", "%20"))
    val rawPath = if (uri.getScheme == null) Paths.get(rid) else Paths.get(uri)
    val resourcePath = if (rawPath.isAbsolute) {
      rawPath.toAbsolutePath
    } else {
      basePath.resolve(rawPath).normalize().toAbsolutePath
    }
    if (! resourcePath.toFile.exists) {
      val msg = s"resource ${resourcePath.toString} is missing"
      if (ignoreMissing) {
        logger.error(msg); 0
      } else {
        throw new Exception(msg)
      }
    } else {
      val paths = relativizeResourcePath(rawPath, basePath, destPath, archiveRootPath)
      val (finalPath, resourceDestPath) = (paths._1.toString, paths._2.toString)
      res.setResourceId(finalPath)
      if (haveFiles contains resourceDestPath) {
        logger.info(s"skipping duplicate file $resourceDestPath"); 0
      } else {
        logger.info(s"writing $resourceDestPath")
        haveFiles += resourceDestPath
        writeFile(out, resourcePath, resourceDestPath)
      }
    }
  }

  private def writeDataSetImpl(out: ZipOutputStream,
                               ds: DataSetType,
                               dsPath: Path,
                               dsOutPath: String,
                               dsType: DataSetMetaTypes.DataSetMetaType,
                               archiveRootPath: Option[Path],
                               skipMissingFiles: Boolean): Int = {
    val basePath = dsPath.getParent
    val destPath = Option(Paths.get(dsOutPath).getParent).getOrElse(Paths.get(""))
    val dsId = UUID.fromString(ds.getUniqueId)
    val dsTmp = Files.createTempFile(s"relativized-${dsId}", ".xml")
    val nbytes = Option(ds.getExternalResources).map { extRes =>
      extRes.getExternalResource.map { er =>
        writeResourceFile(out, destPath, er, basePath, archiveRootPath, skipMissingFiles) +
        Option(er.getExternalResources).map { extRes2 =>
          extRes2.getExternalResource.map { rr =>
            writeResourceFile(out, destPath, rr, basePath, archiveRootPath, skipMissingFiles) +
            Option(rr.getFileIndices).map { fi =>
              fi.getFileIndex.map {
                writeResourceFile(out, destPath, _, basePath, archiveRootPath, skipMissingFiles)
              }.sum
            }.getOrElse(0)
          }.sum
        }.getOrElse(0) + Option(er.getFileIndices).map { fi =>
          fi.getFileIndex.map {
            writeResourceFile(out, destPath, _, basePath, archiveRootPath, skipMissingFiles)
          }.sum
        }.getOrElse(0)
      }.sum
    }.getOrElse(0)
    DataSetWriter.writeDataSet(dsType, ds, dsTmp)
    writeFile(out, dsTmp, dsOutPath) + nbytes
  }

  protected def writeDataSet(out: ZipOutputStream,
                             dsPath: Path,
                             dsOutPath: Path,
                             dsType: DataSetMetaTypes.DataSetMetaType,
                             archiveRootPath: Option[Path],
                             skipMissingFiles: Boolean = false): Int = {
    val ds = ImplicitDataSetLoader.loaderAndResolveType(dsType, dsPath)
    writeDataSetImpl(out, ds, dsPath, dsOutPath.toString, dsType, archiveRootPath, skipMissingFiles)
  }

  /**
   * Identical to writeDataSet except the output path is automatically
   * generated from the UUID and base file name.
   */
  protected def writeDataSetAuto(out: ZipOutputStream,
                                 dsPath: Path,
                                 dsType: DataSetMetaTypes.DataSetMetaType,
                                 skipMissingFiles: Boolean = false): Int = {
    val ds = ImplicitDataSetLoader.loaderAndResolveType(dsType, dsPath)
    val dsId = UUID.fromString(ds.getUniqueId)
    val dsOutPath = s"${dsId}/${dsPath.getFileName.toString}"
    writeDataSetImpl(out, ds, dsPath, dsOutPath, dsType, None,
                     skipMissingFiles = skipMissingFiles)
  }
}

object ExportDataSets extends DataSetExporter {
  def apply(datasets: Seq[Path],
            dsType: DataSetMetaTypes.DataSetMetaType,
            zipPath: Path,
            skipMissingFiles: Boolean = false): Int = {
    val out = newZip(zipPath)
    val n = datasets.map(writeDataSetAuto(out, _, dsType, skipMissingFiles)).sum
    out.close
    logger.info(s"wrote $n bytes")
    n
  }
}
