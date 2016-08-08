
/*
 * Interface to external PacBioTestData library
 *
 */

package com.pacbio.secondary.analysis.externaltools

import java.nio.file.{Paths, Files, Path}

import com.typesafe.config.{Config, ConfigFactory}

import spray.json._

import scala.io.Source


case class TestDataFile(id: String, path: String, fileTypeId: String,
                        description: String)

trait TestDataJsonProtocol extends DefaultJsonProtocol {
  implicit val testDataFileFormat = jsonFormat4(TestDataFile)
}

class PacBioTestData(files: Seq[TestDataFile], base: Path) {
  val fileLookup = files.map(f => (f.id, f)).toMap

  def getFile(id: String): Path = {
    val relPath = fileLookup(id).path
    Paths.get(base.toString, relPath)
  }
}

object PacBioTestData extends TestDataJsonProtocol {

  lazy val conf = ConfigFactory.load()

  private def getFilesJson = Paths.get(conf.getString("pb-test.test-files"))

  def isAvailable: Boolean = Files.isRegularFile(getFilesJson)

  def apply() = {
    val filesJson = getFilesJson
    val json = Source.fromFile(filesJson.toFile).getLines.mkString.parseJson
    val files = json.convertTo[Seq[TestDataFile]]
    new PacBioTestData(files, filesJson.getParent)
  }
}
