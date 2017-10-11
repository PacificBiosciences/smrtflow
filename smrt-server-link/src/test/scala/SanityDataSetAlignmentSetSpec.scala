import java.nio.file.{Path, Paths}

import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetLoader,
  DataSetValidator
}
import com.pacificbiosciences.pacbiodatasets._
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

/**
  * Sannity test to load dataset test files and XML -> DataSet Object
  * Created by mkocher on 5/29/15.
  */
class SanityDataSetAlignmentSetSpec extends Specification with LazyLogging {

  sequential

  "Sanity DataSet Loading" should {

    // There's more specific tests in ReferenceDataSetSpec
    "Alignment dataset loading " in {
      val rootDir = "/dataset-alignments"

      def loadDs(path: Path): AlignmentSet = {
        logger.info(s"loading reference datasets from $path")
        val ds = DataSetLoader.loadAlignmentSet(path)
        val name = ds.getName
        DataSetValidator.validate(ds, path.getParent)
        logger.info(s"successfully loaded subread dataset $ds")
        //val jstring = DataSetJsonUtils.subreadSetToJson(ds)
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
