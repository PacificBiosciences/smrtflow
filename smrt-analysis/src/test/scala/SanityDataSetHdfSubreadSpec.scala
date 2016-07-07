import java.nio.file.DirectoryStream.Filter
import java.nio.file.{Path, Files, Paths}

import com.pacbio.secondary.analysis.datasets.io.{DataSetValidator, DataSetJsonUtils, DataSetLoader}
import com.pacificbiosciences.pacbiodatasets._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.specs2.mutable.Specification

/**
 * Sannity test to load dataset test files and XML -> DataSet Object
 * Created by mkocher on 5/29/15.
 */
class SanityDataSetHdfSubreadSpec extends Specification with LazyLogging {

  sequential

  "Sanity DataSet Loading" should {
    "HdfSubread dataset loading" in {
      val rootDir = "/dataset-hdfsubreads"

      def loadDs(path: Path): HdfSubreadSet = {
        logger.info(s"loading hdfsubread datasets from $path")
        val ds = DataSetLoader.loadHdfSubreadSet(path)
        logger.info(s"successfully loaded hdfsubread dataset $ds")
        DataSetValidator.validate(ds, path.getParent)
        val jstring = DataSetJsonUtils.hdfSubreadSetToJson(ds)
        //println(s"HdfSubread json $jstring")
        ds
      }

      val root = getClass.getResource(rootDir)
      val p = Paths.get(root.toURI)
      val files = p.toFile.listFiles.filter(_.isFile).toList
      logger.info(s"Loading datasets from $p")
      val datasets = files.map(x => loadDs(x.toPath))
      true must beTrue
    }
  }
}
