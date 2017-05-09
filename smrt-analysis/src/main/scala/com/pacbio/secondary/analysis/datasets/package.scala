package com.pacbio.secondary.analysis

import java.util.UUID
import java.nio.file.{Path, Paths}
import java.io.File

import scala.xml.{Elem,XML}
import scala.util.{Try,Failure,Success}

/**
 *
 * Created by mkocher on 9/29/15.
 */
package object datasets {

  case class InValidDataSetError(msg: String) extends Exception(msg)
}


trait DataSetFileUtils {
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
