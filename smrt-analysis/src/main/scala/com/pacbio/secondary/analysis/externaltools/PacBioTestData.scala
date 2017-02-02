
/*
 * Interface to external PacBioTestData library
 *
 */

package com.pacbio.secondary.analysis.externaltools

import java.nio.file.{Files, Path, Paths}

import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import spray.json._

import scala.io.Source


case class TestDataFile(id: String, path: String, fileTypeId: String,
                        description: String)

trait TestDataJsonProtocol extends DefaultJsonProtocol {
  implicit val testDataFileFormat = jsonFormat4(TestDataFile)
}

class PacBioTestData(files: Seq[TestDataFile], base: Path) {
  private val fileLookup = files.map(f => (f.id, f)).toMap

  def getFile(id: String): Path = {
    val relPath = fileLookup(id).path
    Paths.get(base.toString, relPath)
  }
}

object PacBioTestData extends TestDataJsonProtocol with ConfigLoader{

  //Can be set via export PB_TEST_DATA_FILES=$(readlink -f PacBioTestData/data/files.json)
  final val PB_TEST_ID = "smrtflow.test.test-files"

  final lazy val testFileDir = conf.getString(PB_TEST_ID)

  // This is the file.json manifest of the Test files. PacBioTestData/data/files.json
  private def getFilesJson = Paths.get(testFileDir)

  def isAvailable: Boolean = Files.isRegularFile(getFilesJson)

  val errorMessage = s"Unable to find PacbioTestData files.json from $testFileDir. Set $PB_TEST_ID to /path/PacBioTestData/data/files.json"

  def apply() = {
    val filesJson = getFilesJson
    val json = Source.fromFile(filesJson.toFile).getLines.mkString.parseJson
    val files = json.convertTo[Seq[TestDataFile]]
    new PacBioTestData(files, filesJson.getParent)
  }
}
