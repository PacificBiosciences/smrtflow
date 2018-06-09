package com.pacbio.secondary.smrtlink.io

import java.io.File
import java.nio.file.{Path, Paths}

import spray.json._
import DefaultJsonProtocol._
import org.apache.commons.io.FileUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes

trait ModelIOUtils {

  import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobProtocols._

  private def resolvePath(f: Path, root: Path): Path = {
    if (f.isAbsolute) f
    else root.resolve(f)
  }

  private def resolve(root: Path,
                      files: Seq[DataStoreFile]): Seq[DataStoreFile] =
    files.map(f =>
      f.copy(path = resolvePath(Paths.get(f.path), root).toString))

  def loadFiles(files: Seq[DataStoreFile],
                root: Option[Path]): Seq[DataStoreFile] = {
    val resolvedFiles = root.map(r => resolve(r, files)).getOrElse(files)
    resolvedFiles
      .filter(_.fileTypeId == FileTypes.DATASTORE.fileTypeId)
      .foldLeft(resolvedFiles) { (f, dsf) =>
        f ++ loadDataStoreFilesFromDataStore(Paths.get(dsf.path).toFile)
      }
  }

  def loadDataStoreFilesFromDataStore(file: File): Seq[DataStoreFile] = {
    //logger.info(s"Loading raw DataStore from $file")
    val sx = FileUtils.readFileToString(file, "UTF-8")
    val ds = sx.parseJson.convertTo[PacBioDataStore]
    loadFiles(ds.files, Some(file.toPath.getParent))
      .map(f => f.uniqueId -> f)
      .toMap
      .values
      .toSeq
  }

}

object ModelIOUtils extends ModelIOUtils
