import java.nio.file.{Files, Paths, Path}

import collection.JavaConversions._
import collection.JavaConverters._

import com.pacbio.secondary.analysis.datasets.io.{DataSetWriter, DataSetMerger, DataSetLoader}
import com.pacbio.secondary.analysis.tools.{DataSetMergerOptions, DataSetMergerTool}
import com.pacbio.secondary.analysis.externaltools.PacBioTestData

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

      // Not really clear what the expected behavior is here. The Schema of the HdfSubreadSet has not changed
      // but the DataSet "version" is across all schemas.
      mergedDataSet.getVersion must beEqualTo("4.0.0")
      mergedDataSet.getExternalResources.getExternalResource.length must beEqualTo(6)
      mergedDataSet.getDataSetMetadata.getTotalLength must beEqualTo(150000000)
      mergedDataSet.getDataSets.getDataSet.size must beEqualTo(3)
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

class DataSetMergerAdvancedSpec extends Specification with LazyLogging {
  args(skipAll = !PacBioTestData.isAvailable)

  sequential

  private def getData(dsIds: Seq[String]): Seq[Path] = {
    val pbdata = PacBioTestData()
    dsIds.map(pbdata.getFile(_))
  }

  "Test merging additional dataset types" should {
    "Merge SubreadSets" in {
      val paths = getData(Seq("subreads-sequel", "subreads-xml"))
      val datasets = paths.map(x => DataSetLoader.loadSubreadSet(x))
      val name = "Merged Datasets"
      logger.info(s"Loaded datasets $datasets")
      val mergedDataSet = DataSetMerger.mergeSubreadSets(datasets, "ds-name")
      println(s"Dataset mergedDataSet $mergedDataSet")
      val p = Files.createTempFile("merged", "subreadset.xml")
      logger.info(s"Writing merged dataset to $p")
      DataSetWriter.writeSubreadSet(mergedDataSet, p)
      mergedDataSet.getVersion must beEqualTo("4.0.0")
      mergedDataSet.getExternalResources.getExternalResource.length must beEqualTo(2)
      mergedDataSet.getDataSetMetadata.getTotalLength must beEqualTo(81354)
      mergedDataSet.getDataSetMetadata.getNumRecords must beEqualTo(137)
      mergedDataSet.getDataSets.getDataSet.size must beEqualTo(2)
    }
    "Merge AlignmentSets" in {
      val paths = getData(Seq("aligned-xml", "aligned-ds-2"))
      val datasets = paths.map(x => DataSetLoader.loadAlignmentSet(x))
      val name = "Merged Datasets"
      logger.info(s"Loaded datasets $datasets")
      val mergedDataSet = DataSetMerger.mergeAlignmentSets(datasets, "ds-name")
      println(s"Dataset mergedDataSet $mergedDataSet")
      val p = Files.createTempFile("merged", "alignmentset.xml")
      logger.info(s"Writing merged dataset to $p")
      DataSetWriter.writeAlignmentSet(mergedDataSet, p)
      mergedDataSet.getVersion must beEqualTo("4.0.0")
      mergedDataSet.getExternalResources.getExternalResource.length must beEqualTo(3)
      //FIXME Metadata isn't being handled properly right now
      //mergedDataSet.getDataSetMetadata.getTotalLength must beEqualTo(274217)
      //mergedDataSet.getDataSetMetadata.getNumRecords must beEqualTo(133)
      mergedDataSet.getDataSets.getDataSet.size must beEqualTo(2)
    }
  }
}
