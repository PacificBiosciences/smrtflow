package com.pacbio.secondary.smrtlink.analysis

import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.io.File

import org.apache.commons.io.{FileUtils, FilenameUtils}
import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  PacBioTestResources,
  PacBioTestResourcesLoader
}

import scala.util.{Failure, Success, Try}

/**
  *
  * Created by mkocher on 9/29/15.
  */
package object datasets {

  case class InValidDataSetError(msg: String) extends Exception(msg)

  // Mini metadata
  case class DataSetMiniMeta(uuid: UUID,
                             metatype: DataSetMetaTypes.DataSetMetaType)

  trait DataSetFileUtils {

    /**
      *
      * Extra the minimal metadata from the DataSet. This is centralized to have a single loading and parsing
      * of the PacBio DataSet XML.
      *
      * This is java-ish model that raises, callers should use wrap in Try
      *
      * @param path Path to the DataSet
      * @return
      */
    def getDataSetMiniMeta(path: Path): DataSetMiniMeta = {
      // This should be a streaming model to parse the XML
      val xs = scala.xml.XML.loadFile(path.toFile)

      val uniqueId = xs.attributes("UniqueId").toString()
      val m = xs.attributes("MetaType").toString()

      val uuid = UUID.fromString(uniqueId)

      val errorMessage =
        s"Couldn't parse dataset MetaType from '$m' as an XML file: $path"

      val dsMeta = DataSetMetaTypes
        .toDataSetType(m)
        .getOrElse(throw new IllegalArgumentException(errorMessage))

      DataSetMiniMeta(uuid, dsMeta)
    }

    private def parseXml(path: Path) = {
      Try { scala.xml.XML.loadFile(path.toFile) } match {
        case Success(x) => x
        case Failure(err) =>
          throw new IllegalArgumentException(
            s"Couldn't parse ${path.toString} as an XML file: ${err.getMessage}")
      }
    }

    /**
      * Parse an RSII metadata.xml file to extract the run name.
      *
      * @param path RSII movie.metadata.xml
      * @return
      */
    def dsNameFromRsMetadata(path: Path): String = {
      if (!path.toString.endsWith(".metadata.xml"))
        throw new Exception(
          s"File {p} lacks the expected extension (.metadata.xml)")
      val md = scala.xml.XML.loadFile(path.toFile)
      if (md.label != "Metadata")
        throw new Exception(
          s"The file ${path.toString} does not appear to be an RS II metadata XML")
      (md \ "Run" \ "Name").text
    }
  }

  object DataSetFileUtils extends DataSetFileUtils

  /** Utilities for setting up test datasets that can be safely manipulated or
    * deleted
    *
    */
  object MockDataSetUtils {

    // copy all files associated with a dataset to the destination directory
    // based on file-name prefix, e.g. movie name.  if copyAll is true, it
    // will copy everything in the source directory.
    private def copyResources(dsPath: Path,
                              destDir: File,
                              copyAll: Boolean = false) = {
      val dsDir = dsPath.getParent.toFile
      val prefix = FilenameUtils.getName(dsPath.toString).split('.')(0)
      for (f <- dsDir.listFiles) {
        val filename = FilenameUtils.getName(f.toString)
        if (copyAll || filename.startsWith(prefix)) {
          val dest = new File(destDir.toString + "/" + filename)
          FileUtils.copyFile(f, dest)
        }
      }
    }

    /**
      * copy a SubreadSet and BarcodeSet from PacBioTestData to a target dir,
      * either passed as an option or a new temporary directory
      * @param destDir  optional destination, defaults to temp dir
      */
    def makeBarcodedSubreads(testResources: PacBioTestResources,
                             destDir: Option[Path]): (Path, Path) = {

      val targetDir =
        destDir.getOrElse(Files.createTempDirectory("dataset-contents"))
      val subreadsDestDir = new File(targetDir.toString + "/SubreadSet")
      val barcodesDestDir = new File(targetDir.toString + "/BarcodeSet")
      val subreadsSrc = testResources.findById("barcoded-subreadset").get.path
      val subreadsDir = subreadsSrc.getParent.toFile
      val barcodesSrc = testResources.findById("barcodeset").get.path
      val barcodesDir = barcodesSrc.getParent.toFile
      // only copy the files we need for this SubreadSet, that way we can check
      // for an empty directory
      copyResources(subreadsSrc, subreadsDestDir)
      FileUtils.copyDirectory(barcodesDir, barcodesDestDir)
      val subreads = Paths.get(
        subreadsDestDir.toString + "/" +
          FilenameUtils.getName(subreadsSrc.toString))
      val barcodes = Paths.get(
        barcodesDestDir.toString + "/" +
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

    def makeBarcodedSubreads(
        testResources: PacBioTestResources): (Path, Path) =
      makeBarcodedSubreads(testResources, None)

    def makeTmpDataset(dsPath: Path,
                       metaType: DataSetMetaTypes.DataSetMetaType,
                       setNewUuid: Boolean = true,
                       copyFiles: Boolean = true,
                       tmpDirBase: String = "dataset-contents"): Path = {
      // XXX The space in the pathname is deliberate (see SL-1586)
      val targetDir = Files.createTempDirectory(tmpDirBase)
      val dsTmp = Paths.get(
        targetDir.toString + "/" +
          FilenameUtils.getName(dsPath.toString))
      val ds = if (!copyFiles) {
        ImplicitDataSetLoader.loaderAndResolveType(metaType, dsPath)
      } else {
        DataSetLoader.loadType(metaType, dsPath)
      }
      // NOTE this assumes that external resources are located in the same
      // directory
      if (copyFiles) copyResources(dsPath, targetDir.toFile, true)
      if (setNewUuid) ds.setUniqueId(UUID.randomUUID().toString)
      DataSetWriter.writeDataSet(metaType, ds, dsTmp)
      dsTmp
    }
  }

}
