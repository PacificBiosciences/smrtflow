
package com.pacbio.secondary.analysis.datasets.io

import java.nio.file.{Files, Path, Paths}
import java.io._
import java.net.URI
import java.util.UUID
import java.util.zip._

import com.typesafe.scalalogging.LazyLogging
import collection.JavaConversions._
import collection.JavaConverters._

import com.pacbio.secondary.analysis.datasets._
import com.pacificbiosciences.pacbiobasedatamodel.InputOutputDataType


class ExportDataSets(
      datasets: Seq[Path],
      dsType: DataSetMetaTypes.DataSetMetaType,
      zipPath: Path)
    extends LazyLogging {

  val dest = new FileOutputStream(zipPath.toFile)
  val out = new ZipOutputStream(new BufferedOutputStream(dest))

  private def getResourcePath(basePath: Path, resourceId: String): Path = {
    val resPath = if (resourceId.startsWith("file://")) Paths.get(URI.create(resourceId)) else Paths.get(resourceId)
    if (! resPath.isAbsolute) basePath.resolve(resPath) else resPath
  }

  private def writeFile(path: Path, zipOutPath: String): Int = {
    val ze = new ZipEntry(zipOutPath)
    out.putNextEntry(ze)
    val data = Files.readAllBytes(path)
    out.write(data, 0, data.size)
    data.size
  }

  private def writeResourceFile(dsId: UUID, res: InputOutputDataType, basePath: Path): Int = {
    val resourcePath = getResourcePath(basePath, res.getResourceId)
    logger.info(s"writing $resourcePath")
    res.setResourceId(resourcePath.getFileName.toString)
    writeFile(resourcePath, s"${dsId}/${res.getResourceId}")
  }

  def write: Int = {
    val n = datasets.map { dsPath =>
      val basePath = dsPath.getParent
      val ds = DataSetLoader.loadType(dsType, dsPath)
      val dsId = UUID.fromString(ds.getUniqueId)
      val dsOutPath = s"${dsId}/${dsPath.getFileName.toString}"
      val dsTmp = Files.createTempFile(s"relativized-${dsId}", ".xml")
      val nbytes = Option(ds.getExternalResources).map { extRes =>
        extRes.getExternalResource.map { er =>
          writeResourceFile(dsId, er, basePath) +
          Option(er.getExternalResources).map { extRes2 =>
            extRes2.getExternalResource.map { rr =>
              writeResourceFile(dsId, rr, basePath) +
              Option(rr.getFileIndices).map { fi =>
                fi.getFileIndex.map {
                  writeResourceFile(dsId, _, basePath)
                }.sum
              }.getOrElse(0)
            }.sum
          }.getOrElse(0) + Option(er.getFileIndices).map { fi =>
            fi.getFileIndex.map {
              writeResourceFile(dsId, _, basePath)
            }.sum
          }.getOrElse(0)
        }.sum
      }.getOrElse(0)
      DataSetWriter.writeDataSet(dsType, ds, dsTmp)
      writeFile(dsTmp, dsOutPath) + nbytes
    }.sum
    out.close
    logger.info(s"wrote $n bytes")
    n
  }
}

object ExportDataSets {
  def apply(datasets: Seq[Path],
            dsType: DataSetMetaTypes.DataSetMetaType,
            zipPath: Path): Int = {
    val writer = new ExportDataSets(datasets, dsType, zipPath)
    writer.write
  }
}
