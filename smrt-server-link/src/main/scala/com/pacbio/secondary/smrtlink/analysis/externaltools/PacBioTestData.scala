package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.nio.file.{Files, Path, Paths}

import com.pacbio.common.models.PathProtocols
import spray.json._
import org.apache.commons.io.FileUtils
import com.pacbio.secondary.smrtlink.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetFileUtils,
  MockDataSetUtils
}

// The fileTypeId can't be added as a type without adding
// a custom serialization mechanism and a global registry of
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

case class PacBioTestResources(files: Seq[TestDataResource]) {
  def findById(id: String): Option[TestDataResource] = files.find(_.id == id)

  def getByIds(ids: Set[String]): Seq[TestDataResource] =
    files.filter(f => ids contains f.id)

  def getFilesByType(fileType: FileTypes.FileType): Seq[TestDataResource] =
    files.filter(_.fileTypeId == fileType.fileTypeId)
}

trait TestDataResourceJsonProtocol extends PathProtocols {
  implicit val testDataResourceFormat = jsonFormat4(TestDataResource)
}

/**
  *
  *
  * And this needs to be moved to a better namespace/location (not externaltools)
  */
object PacBioTestResourcesLoader
    extends TestDataResourceJsonProtocol
    with ConfigLoader {

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
  def loadFromJsonPath(p: Path): PacBioTestResources = {
    val json = FileUtils.readFileToString(p.toFile).parseJson
    val files = json.convertTo[Seq[TestDataResource]]

    val root = p.toAbsolutePath.getParent

    val resolvedFiles = files.map { f =>
      f.copy(path = resolver(f.path, root))
    }

    PacBioTestResources(resolvedFiles)
  }

  def loadFromConfig(): PacBioTestResources = {
    loadFromJsonPath(getFilesJson)
  }
}
