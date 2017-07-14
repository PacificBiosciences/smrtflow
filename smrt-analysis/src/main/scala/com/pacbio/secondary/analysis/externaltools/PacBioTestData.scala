
/*
 * Interface to external PacBioTestData library
 *
 */

package com.pacbio.secondary.analysis.externaltools

import java.nio.file.{Files, Path, Paths}


import spray.json._
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.analysis.datasets.{DataSetFileUtils, DataSetMetaTypes, MockDataSetUtils}
import org.apache.commons.io.FileUtils


case class TestDataFile(id: String, path: String, fileTypeId: String,
                        description: String)

trait TestDataJsonProtocol extends DefaultJsonProtocol {
  implicit val testDataFileFormat = jsonFormat4(TestDataFile)
}

/**
  * This is mixing up the IO layer with the data model layer.
  * When the TestDataFile instances are created, the absolute path
  * should be resolved. This would avoid have to require base directory
  * to be passed in. Moreover, it creates friction with how this class should be
  * used.
  *
  * @param files List of TestDataFiles, this must be consistent with the base directory is supplied
  * @param base Root absolute path of any relative Path provided by a TestDataFile
  */
case class PacBioTestData(files: Seq[TestDataFile], base: Path)
    extends DataSetFileUtils {
  private val fileLookup = files.map(f => (f.id, f)).toMap

  def getFile(id: String): Path = {
    val relPath = fileLookup(id).path
    Paths.get(base.toString, relPath)
  }

  // Copy a dataset to a temporary directory, optionally including all
  // associated files.
  def getTempDataSet(id: String, copyFiles: Boolean = false): Path = {
    val path = getFile(id)
    val dst = getDataSetMiniMeta(path).metatype
    MockDataSetUtils.makeTmpDataset(path, dst, copyFiles = copyFiles)
  }
}

object PacBioTestData extends TestDataJsonProtocol with ConfigLoader{

  //Can be set via export PB_TEST_DATA_FILES=$(readlink -f PacBioTestData/data/files.json)
  final val PB_TEST_ID = "smrtflow.test.test-files"

  final lazy val testFileDir = conf.getString(PB_TEST_ID)

  // This is the file.json manifest of the Test files. PacBioTestData/data/files.json
  private def getFilesJson = Paths.get(testFileDir)

  // FIXME. This method doesn't make sense when the instance is created from the raw constructor.
  def isAvailable: Boolean = Files.isRegularFile(getFilesJson)

  val errorMessage = s"Unable to find PacbioTestData files.json from $testFileDir. Set $PB_TEST_ID or env var 'PB_TEST_DATA_FILES' to /path/PacBioTestData/data/files.json"

  /**
    * Load files.json from the application.conf file
    * @return
    */
  def apply():PacBioTestData = apply(getFilesJson)

  /**
    * Load a PacBioTest Data from files.json
    * @param filesJson Absolute path to the files.json
    * @return
    */
  def apply(filesJson: Path):PacBioTestData = {
    val json = FileUtils.readFileToString(filesJson.toFile).parseJson
    val files = json.convertTo[Seq[TestDataFile]]
    new PacBioTestData(files, filesJson.toAbsolutePath.getParent)
  }
}
