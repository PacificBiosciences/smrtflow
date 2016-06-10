import java.nio.file.{Files, Paths}

import collection.JavaConversions._
import collection.JavaConverters._

import com.pacbio.secondary.analysis.datasets.io.{DataSetWriter, DataSetMerger, DataSetLoader}
import com.pacbio.secondary.analysis.tools.{DataSetMergerOptions, DataSetMergerTool}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

/**
 *
 * Created by mkocher on 5/15/15.
 */
class DataSetMergerSpec extends Specification with LazyLogging {

  sequential
    // Added duplicate files to test if merging of duplicate resources
    val exampleFiles = Seq(
        "m140913_222218_42240_c100699952400000001823139203261564_s1_p0.hdfsubread.dataset.xml",
        "m150404_101626_42267_c100807920800000001823174110291514_s1_p0.hdfsubread.dataset.xml",
        "m150404_101626_42267_c100807920800000001823174110291514_s1_p0.hdfsubread.dataset.xml.copy"
    )

  val examplePaths = exampleFiles.map(x => Paths.get(getClass.getResource("dataset-hdfsubreads/" + x).toURI))

  "Sanity test for merging datasets" should {
    "Merge Hdf Subread" in {
      val datasets = examplePaths.map(x => DataSetLoader.loadHdfSubreadSet(x))

      val name = "Merged Datasets"
      logger.info(s"Loaded datasets $datasets")

      val mergedDataSet = DataSetMerger.mergeHdfSubreadSets(datasets, "ds-name")

      println(s"Dataset mergedDataSet $mergedDataSet")

      val p = Files.createTempFile("subread", "dataset.xml")
      logger.info(s"Writing merged dataset to $p")
      DataSetWriter.writeHdfSubreadSet(mergedDataSet, p)

      mergedDataSet.getVersion must beEqualTo("3.2.0")
      mergedDataSet.getExternalResources.getExternalResource.length must beEqualTo(6)
      mergedDataSet.getDataSetMetadata.getTotalLength must beEqualTo(150000000)
    }
  }
  "Merge dataset tool smoke test" should {
    "Simple HdfSubread merge" in {
      val paths = examplePaths.map(_.toFile)
      val outputPath = Files.createTempFile("hdfsubread", "dataset.xml")
      val opts = DataSetMergerOptions("PacBio.DataSet.HdfSubreadSet", paths, outputPath.toAbsolutePath.toString)
      val result = DataSetMergerTool.run(opts)
      logger.info(s"Merge tool Results $result")
      result.isRight must beTrue
    }

  }

}
