package com.pacbio.secondary.analysis

import java.util.UUID
import java.nio.file.{Path, Paths}
import java.io.File

import com.pacbio.secondary.analysis.datasets.{DataSetMetaTypes, DataSetMiniMeta}

import scala.xml.{Elem, XML}
import scala.util.{Failure, Success, Try}

/**
 *
 * Created by mkocher on 9/29/15.
 */
package object datasets {

  case class InValidDataSetError(msg: String) extends Exception(msg)

  // Mini metadata
  case class DataSetMiniMeta(uuid: UUID, metatype: DataSetMetaTypes.DataSetMetaType)
}


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

    val errorMessage = s"Couldn't parse dataset MetaType from '$m' as an XML file: $path"

    val dsMeta = DataSetMetaTypes.toDataSetType(m)
        .getOrElse(throw new IllegalArgumentException(errorMessage))

    DataSetMiniMeta(uuid, dsMeta)
  }

  private def parseXml(path: Path) = {
    Try { scala.xml.XML.loadFile(path.toFile) } match {
      case Success(x) => x
      case Failure(err) => throw new IllegalArgumentException(s"Couldn't parse ${path.toString} as an XML file: ${err.getMessage}")
    }
  }

  private def getAttribute(e: Elem, attr: String): String = {
    Try { e.attributes(attr).toString } match {
      case Success(a) => a
      case Failure(err) => throw new Exception(s"Can't retrieve $attr attribute from XML: ${err.getMessage}.  Please make sure this is a valid PacBio DataSet XML file.")
    }
  }

  // FIXME this should probably return a DataSetMetaType
  def dsMetaTypeFromPath(path: Path): String =
    getAttribute(parseXml(path), "MetaType")

  def dsUuidFromPath(path: Path): UUID =
    java.util.UUID.fromString(getAttribute(parseXml(path), "UniqueId"))

  def dsNameFromMetadata(path: Path): String = {
    if (! path.toString.endsWith(".metadata.xml")) throw new Exception(s"File {p} lacks the expected extension (.metadata.xml)")
    val md = scala.xml.XML.loadFile(path.toFile)
    if (md.label != "Metadata") throw new Exception(s"The file ${path.toString} does not appear to be an RS II metadata XML")
    (md \ "Run" \ "Name").text
  }
}
