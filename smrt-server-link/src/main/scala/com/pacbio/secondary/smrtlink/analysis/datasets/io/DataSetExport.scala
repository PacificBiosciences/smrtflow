package com.pacbio.secondary.smrtlink.analysis.datasets.io

import java.nio.file.{Files, Path, Paths}
import java.io._
import java.net.URI
import java.util.UUID
import java.util.zip._

import scala.collection.mutable

import com.typesafe.scalalogging.LazyLogging
import collection.JavaConverters._

import com.pacbio.secondary.smrtlink.analysis.datasets._
import com.pacificbiosciences.pacbiobasedatamodel.{
  InputOutputDataType,
  IndexedDataType
}
import com.pacificbiosciences.pacbiodatasets.DataSetType

/**
  * Miscellaneous functions essential for exporting datasets and other file
  * types.
  */
trait ExportUtils {

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
    * @return tuple of (newResourcePath, zipDestPath)
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
      } else if (archiveRoot.isDefined) {
        if (resource.startsWith(archiveRoot.get)) {
          // not a subdirectory of the base path, but part of the same archival
          // directory
          (basePath.relativize(resource),
           archiveRoot.get.relativize(resource).normalize())
        } else {
          val resourceDestPath = Paths.get(s"external-resources/${resource}")
          val finalPath =
            basePath
              .relativize(archiveRoot.get.resolve(resourceDestPath))
              .normalize()
          (finalPath, resourceDestPath)
        }
      } else {
        val resourceDestPath = Paths.get(s"./${resource}")
        (resourceDestPath, destPath.resolve(resourceDestPath).normalize())
      }
    } else {
      // FIXME what if the resource is relative and outside basePath?
      (resource, destPath.resolve(resource).normalize())
    }
  }

  private def getIndices(res: IndexedDataType): Seq[InputOutputDataType] = {
    Option(res.getFileIndices)
      .map { fi =>
        fi.getFileIndex.asScala.toSeq
      }
      .getOrElse(Seq.empty[InputOutputDataType])
  }

  protected def getResources(ds: DataSetType): Seq[InputOutputDataType] = {
    Option(ds.getExternalResources)
      .map { extRes =>
        extRes.getExternalResource.asScala.map { er =>
          Seq(er) ++
            Option(er.getExternalResources)
              .map { extRes2 =>
                extRes2.getExternalResource.asScala.map { rr =>
                  Seq(rr) ++ getIndices(rr)
                }.flatten
              }
              .getOrElse(Seq.empty[InputOutputDataType]) ++ getIndices(er)
        }.flatten
      }
      .getOrElse(Seq.empty[InputOutputDataType])
  }
}

/**
  * Core zip export machinery, independent of input type
  */
abstract class ExportBase(zipPath: Path) extends ExportUtils with LazyLogging {
  protected val haveFiles = mutable.Set.empty[String]
  protected val BUFFER_SIZE = 2048

  private val dest = new FileOutputStream(zipPath.toFile)
  val out = new ZipOutputStream(new BufferedOutputStream(dest))

  def close = out.close

  /**
    * Low-level call for writing the contents of a file to an open zipfile
    * @param path  actual path to input file
    * @param zipOutPath  path to write to the zipfile
    */
  protected def writeFile(path: Path, zipOutPath: String): Long = {
    val ze = new ZipEntry(zipOutPath)
    out.putNextEntry(ze)
    val input = new FileInputStream(path.toString)
    val data = new Array[Byte](BUFFER_SIZE)
    var nRead = -1
    var nWritten: Long = 0
    while ({ nRead = input.read(data); nRead > 0 }) {
      out.write(data, 0, nRead)
      nWritten += nRead
    }
    nWritten
  }

  /**
    * Wrapper for writeFile that guards against redundancy, relativizes the
    * path, and optionally reads contents from an alternate path
    * @param path  canonical path to exported file
    * @param basePath  root directory to export from; paths in zipfile will be
    *                  relative to this
    * @param srcPath  optional path to read from, e.g. if we wrote a temporary
    *                 copy with modifications
    */
  protected def exportFile(path: Path,
                           basePath: Path,
                           srcPath: Option[Path] = None): Long = {
    val destPath = basePath.relativize(path).toString
    if (haveFiles contains destPath) {
      logger.info(s"Skipping duplicate file ${destPath}"); 0L
    } else {
      logger.info(s"Writing file ${destPath} to zip")
      haveFiles += destPath
      writeFile(srcPath.getOrElse(path), destPath)
    }
  }
}

/**
  * Base class for exporting DataSet XML and all external resources to a zip
  * archive.  Used both here and in the job export in JobUtils.scala
  */
abstract class DataSetExporter(zipPath: Path)
    extends ExportBase(zipPath)
    with LazyLogging {

  /*
   * Write a (possibly modified) dataset to a temporary file, then write this
   * file to the ZIP archive.
   */
  private def writeDataSetXml(
      ds: DataSetType,
      dsOutPath: String,
      dsType: DataSetMetaTypes.DataSetMetaType): Long = {
    val dsId = UUID.fromString(ds.getUniqueId)
    val dsTmp = Files.createTempFile(s"relativized-${dsId.toString}", ".xml")
    DataSetWriter.writeDataSet(dsType, ds, dsTmp)
    writeFile(dsTmp, dsOutPath)
  }

  protected def writeResourceFile(destPath: Path,
                                  res: InputOutputDataType,
                                  basePath: Path,
                                  archiveRootPath: Option[Path]): Long = {
    val rid = res.getResourceId
    val uri = URI.create(rid.replaceAll(" ", "%20"))
    val rawPath = if (uri.getScheme == null) Paths.get(rid) else Paths.get(uri)
    val resourcePath = if (rawPath.isAbsolute) {
      rawPath.toAbsolutePath
    } else {
      basePath.resolve(rawPath).normalize().toAbsolutePath
    }
    if (!resourcePath.toFile.exists) {
      val msg = s"resource ${resourcePath.toString} is missing"
      logger.error(msg)
      throw new IOException(msg)
    } else {
      val paths =
        relativizeResourcePath(rawPath, basePath, destPath, archiveRootPath)
      val (finalPath, resourceDestPath) =
        (paths._1.toString, paths._2.toString)
      res.setResourceId(finalPath)
      val resourceDsType = DataSetMetaTypes.fromString(res.getMetaType)
      if (haveFiles contains resourceDestPath) {
        logger.info(s"skipping duplicate file $resourceDestPath"); 0L
      } else {
        logger.info(s"writing $resourceDestPath")
        haveFiles += resourceDestPath
        resourceDsType
          .map { dsType =>
            val ds = DataSetLoader.loadType(dsType, resourcePath)
            val resources = getResources(ds)
            resources.map { er =>
              writeResourceFile(Paths.get(resourceDestPath).getParent,
                                er,
                                resourcePath.getParent,
                                archiveRootPath)
            }.sum + writeDataSetXml(ds, resourceDestPath, dsType)
          }
          .getOrElse(writeFile(resourcePath, resourceDestPath))
      }
    }
  }

  private def writeDataSetImpl(ds: DataSetType,
                               dsPath: Path,
                               dsOutPath: String,
                               dsType: DataSetMetaTypes.DataSetMetaType,
                               archiveRootPath: Option[Path]): Long = {
    val basePath = dsPath.getParent
    val destPath =
      Option(Paths.get(dsOutPath).getParent).getOrElse(Paths.get(""))
    val resources = getResources(ds)
    val nbytes: Long = resources.map { er =>
      writeResourceFile(destPath, er, basePath, archiveRootPath)
    }.sum
    writeDataSetXml(ds, dsOutPath, dsType)
  }

  protected def writeDataSet(dsPath: Path,
                             dsOutPath: Path,
                             dsType: DataSetMetaTypes.DataSetMetaType,
                             archiveRootPath: Option[Path]): Long = {
    val ds = ImplicitDataSetLoader.loaderAndResolveType(dsType, dsPath)
    writeDataSetImpl(ds, dsPath, dsOutPath.toString, dsType, archiveRootPath)
  }

  /**
    * Identical to writeDataSet except the output path is automatically
    * generated from the UUID and base file name.
    */
  protected def writeDataSetAuto(
      dsPath: Path,
      dsType: DataSetMetaTypes.DataSetMetaType): Long = {
    val ds = ImplicitDataSetLoader.loaderAndResolveType(dsType, dsPath)
    val dsId = UUID.fromString(ds.getUniqueId)
    val dsOutPath = s"${dsId}/${dsPath.getFileName.toString}"
    writeDataSetImpl(ds, dsPath, dsOutPath, dsType, None)
  }
}

class ExportDataSets(zipPath: Path) extends DataSetExporter(zipPath)

object ExportDataSets extends LazyLogging {
  def apply(datasets: Seq[Path],
            dsType: DataSetMetaTypes.DataSetMetaType,
            zipPath: Path): Long = {
    val e = new ExportDataSets(zipPath)
    val n = datasets.map(e.writeDataSetAuto(_, dsType)).sum
    e.close
    logger.info(s"wrote $n bytes")
    n
  }
}
