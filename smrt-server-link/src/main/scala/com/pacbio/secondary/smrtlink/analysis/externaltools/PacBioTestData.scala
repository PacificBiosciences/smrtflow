package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.nio.file.{Files, Path, Paths}

import com.pacbio.common.models.PathProtocols
import spray.json._
import org.apache.commons.io.FileUtils
import com.pacbio.secondary.smrtlink.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.{DataSetFileUtils, MockDataSetUtils}

case class TestDataFile(id: String,
                        path: String,
                        fileTypeId: String,
                        description: String)

trait TestDataJsonProtocol extends DefaultJsonProtocol {
  implicit val testDataFileFormat = jsonFormat4(TestDataFile)
}

/**
  * This needs to be moved to a better namespace/location (not externaltools)
  *
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

  def getFilesByType(ft: FileTypes.FileType) =
    files.filter(_.fileTypeId == ft.fileTypeId).map(f => getFile(f.id))

  def getFile(id: String): Path = {
    val relPath = fileLookup(id).path
    Paths.get(base.toString, relPath)
  }

  // Copy a dataset to a temporary directory, optionally including all
  // associated files.
  def getTempDataSet(id: String,
                     copyFiles: Boolean = false,
                     tmpDirBase: String = "dataset-contents"): Path = {
    val path = getFile(id)
    val dst = getDataSetMiniMeta(path).metatype
    MockDataSetUtils.makeTmpDataset(path,
                                    dst,
                                    copyFiles = copyFiles,
                                    tmpDirBase = tmpDirBase)
  }
}

object PacBioTestData extends TestDataJsonProtocol with ConfigLoader {

  //Can be set via export PB_TEST_DATA_FILES=$(readlink -f PacBioTestData/data/files.json)
  final val PB_TEST_ID = "smrtflow.test.test-files"

  final lazy val testFileDir = conf.getString(PB_TEST_ID)

  // This is the file.json manifest of the Test files. PacBioTestData/data/files.json
  private def getFilesJson = Paths.get(testFileDir)

  // FIXME. This method doesn't make sense when the instance is created from the raw constructor.
  def isAvailable: Boolean = Files.isRegularFile(getFilesJson)

  val errorMessage =
    s"Unable to find PacbioTestData files.json from $testFileDir. Set $PB_TEST_ID or env var 'PB_TEST_DATA_FILES' to /path/to/repos/pacbiotestdata/data/files.json"

  /**
    * Load files.json from the application.conf file
    * @return
    */
  def apply(): PacBioTestData = apply(getFilesJson)

  /**
    * Load a PacBioTest Data from files.json
    * @param filesJson Absolute path to the files.json
    * @return
    */
  def apply(filesJson: Path): PacBioTestData = {
    val json = FileUtils.readFileToString(filesJson.toFile).parseJson
    val files = json.convertTo[Seq[TestDataFile]]
    new PacBioTestData(files, filesJson.toAbsolutePath.getParent)
  }
}

// The fileTypeId can't be added as a type without adding
// a custom serialization mechanism and a globaly registry of
// FileTypes.
case class TestDataResource(id: String,
                            path: Path,
                            fileTypeId: String,
                            description: String) {


  /**
    * (FIXME)This should be encoded at the file type level
    * This only works if the File is a DataSet type
    *
    * @param copyFiles
    * @param tmpDirBase
    * @param setNewUuid
    * @return
    */
  def getTempDataSetFile(copyFiles: Boolean = false,
                         tmpDirBase: String = "dataset-contents",
                         setNewUuid: Boolean = false): TestDataResource = {

    val dst = DataSetFileUtils.getDataSetMiniMeta(path).metatype
    val px = MockDataSetUtils.makeTmpDataset(path,
      dst,
      copyFiles = copyFiles,
      tmpDirBase = tmpDirBase,
      setNewUuid = setNewUuid)

    this.copy(path = px)
  }
}

trait TestDataResourceJsonProtocol extends PathProtocols {
  implicit val testDataResourceFormat = jsonFormat4(TestDataResource)
}

case class PacBioTestResources(files: Seq[TestDataResource]) {
  def getFile(id: String): Option[TestDataResource] = files.find(_.id == id)

  def getFilesByType(fileType: FileTypes.FileType): Seq[TestDataResource] =
    files.filter(_.fileTypeId == fileType.fileTypeId)
}


/**
  *
  * This should replace PacBioTestData
  *
  * And this needs to be moved to a better namespace/location (not externaltools)
  */
object PacBioTestResourcesLoader extends TestDataResourceJsonProtocol with ConfigLoader{

  private final val PB_TEST_ID = "smrtflow.test.test-files"

  val ERROR_MESSAGE =
      s"Unable to find PacbioTestData files.json. Set $PB_TEST_ID or env var 'PB_TEST_DATA_FILES' to /path/to/repos/pacbiotestdata/data/files.json"


  final lazy val testFileDir = conf.getString(PB_TEST_ID)

  private def getFilesJson = Paths.get(testFileDir)

  private def resolver(f: Path, root: Path): Path = {
    if (f.isAbsolute) f
    else root.resolve(f)
  }

  def isAvailable(): Boolean = Files.isRegularFile(getFilesJson)
  /**
    * Load a Resource
    *
    * @param p
    */
  def loadFromJsonPath(p: Path) = {
    val json = FileUtils.readFileToString(p.toFile).parseJson
    val files = json.convertTo[Seq[TestDataResource]]

    val root = p.toAbsolutePath.getParent

    val resolvedFiles = files.map { f =>
      f.copy(path = resolver(f.path, root))
    }

    new PacBioTestResources(resolvedFiles)
  }


  def loadFromConfig(): PacBioTestResources = {
    loadFromJsonPath(getFilesJson)
  }
}
