import java.nio.file.{Files, Paths, Path}
import java.io.File
import java.util.UUID

import collection.JavaConversions._
import collection.JavaConverters._

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import org.specs2.mutable._

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{DataSetWriter, DataSetMerger, DataSetLoader}
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.{PacBioTestData,PbReports}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{NullJobResultsWriter, PrinterJobResultsWriter, AnalysisJobStates}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MergeDataSetOptions
import com.pacbio.secondary.smrtlink.analysis.tools.{DataSetMergerOptions, DataSetMergerTool, timeUtils}

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

  val expectedVersion = "4.0.1"

  val examplePaths = exampleFiles.map(x => Paths.get(getClass.getResource("dataset-hdfsubreads/" + x).toURI))

  "Sanity test for merging datasets" should {
    "Merge Hdf Subread" in {
      val datasets = examplePaths.map(x => DataSetLoader.loadHdfSubreadSet(x))

      val name = "Merged Datasets"
      logger.info(s"Loaded datasets $datasets")

      val mergedDataSet = DataSetMerger.mergeHdfSubreadSets(datasets, "ds-name")

      logger.info(s"Dataset mergedDataSet $mergedDataSet")

      val p = Files.createTempFile("merged", ".hdfsubreadset.xml")
      logger.info(s"Writing merged dataset to $p")
      DataSetWriter.writeHdfSubreadSet(mergedDataSet, p)

      // Not really clear what the expected behavior is here. The Schema of the HdfSubreadSet has not changed
      // but the DataSet "version" is across all schemas.
      mergedDataSet.getVersion must beEqualTo(expectedVersion)
      mergedDataSet.getExternalResources.getExternalResource.length must beEqualTo(6)
      mergedDataSet.getDataSetMetadata.getTotalLength must beEqualTo(150000000)
      mergedDataSet.getDataSets.getDataSet.size must beEqualTo(3)
      mergedDataSet.getMetaType must beEqualTo(DataSetMetaTypes.HdfSubread.toString)
    }
  }
  "Merge dataset tool smoke test" should {
    "Simple HdfSubread merge" in {
      val paths = examplePaths.map(_.toFile)
      val outputPath = Files.createTempFile("merged", ".hdfsubreadset.xml")
      val opts = DataSetMergerOptions("PacBio.DataSet.HdfSubreadSet", paths, outputPath.toAbsolutePath.toString)
      val result = DataSetMergerTool.run(opts)
      logger.info(s"Merge tool Results $result")
      result.isRight must beTrue
    }

  }

}

class DataSetMergerAdvancedSpec extends Specification with LazyLogging with timeUtils {
  args(skipAll = !PacBioTestData.isAvailable)

  sequential

  private def getData(dsIds: Seq[String]): Seq[Path] = {
    val pbdata = PacBioTestData()
    dsIds.map(pbdata.getFile(_))
  }

  val expectedVersion = "4.0.1"
  val writer = new NullJobResultsWriter

  "Test merging additional dataset types" should {
    "Merge SubreadSets" in {
      val paths = getData(Seq("subreads-sequel", "subreads-xml"))
      val datasets = paths.map(x => DataSetLoader.loadAndResolveSubreadSet(x))
      val name = "Merged Datasets"
      logger.info(s"Loaded datasets $datasets")
      val mergedDataSet = DataSetMerger.mergeSubreadSets(datasets, "ds-name")
      logger.info(s"Dataset mergedDataSet $mergedDataSet")
      val p = Files.createTempFile("merged", ".subreadset.xml")
      logger.info(s"Writing merged dataset to $p")
      println(p)
      DataSetWriter.writeSubreadSet(mergedDataSet, p)
      mergedDataSet.getVersion must beEqualTo(expectedVersion)
      mergedDataSet.getExternalResources.getExternalResource.length must beEqualTo(2)
      mergedDataSet.getDataSetMetadata.getTotalLength must beEqualTo(81354)
      mergedDataSet.getDataSetMetadata.getNumRecords must beEqualTo(137)
      mergedDataSet.getDataSets.getDataSet.size must beEqualTo(2)
      mergedDataSet.getMetaType must beEqualTo(DataSetMetaTypes.Subread.toString)
    }
    "Merge AlignmentSets" in {
      val paths = getData(Seq("aligned-xml", "aligned-ds-2"))
      val datasets = paths.map(x => DataSetLoader.loadAndResolveAlignmentSet(x))
      val name = "Merged Datasets"
      logger.info(s"Loaded datasets $datasets")
      val mergedDataSet = DataSetMerger.mergeAlignmentSets(datasets, "ds-name")
      logger.info(s"Dataset mergedDataSet $mergedDataSet")
      val p = Files.createTempFile("merged", ".alignmentset.xml")
      logger.info(s"Writing merged dataset to $p")
      DataSetWriter.writeAlignmentSet(mergedDataSet, p)
      mergedDataSet.getMetaType must beEqualTo(DataSetMetaTypes.Alignment.toString)
      mergedDataSet.getVersion must beEqualTo(expectedVersion)
      mergedDataSet.getExternalResources.getExternalResource.length must beEqualTo(3)
      //FIXME Metadata isn't being handled properly right now
      //mergedDataSet.getDataSetMetadata.getTotalLength must beEqualTo(274217)
      //mergedDataSet.getDataSetMetadata.getNumRecords must beEqualTo(133)
      mergedDataSet.getDataSets.getDataSet.size must beEqualTo(2)
    }
    "Merge SubreadSets using jobtype API" in {
      val paths = getData(Seq("subreads-sequel", "subreads-xml"))
      val opts = MergeDataSetOptions(DataSetMetaTypes.Subread.toString,
                                     paths.map(_.toString), "merge_datasets")
      val outputDir = Files.createTempDirectory("merge-job-test")
      val job = JobResource(UUID.randomUUID, outputDir)
      val j = opts.toJob
      val startedAt = JodaDateTime.now()
      val jobResult = j.run(job, writer)
      jobResult.isRight must beTrue
      val elapsed = computeTimeDeltaFromNow(startedAt)
      println(s"Merge job took $elapsed seconds")
      val datastore = jobResult.right.get.asInstanceOf[PacBioDataStore]
      val REPORT_IDS = if (PbReports.isAvailable()) {
        Seq("pbreports.tasks.adapter_report_xml",
            "pbreports.tasks.filter_stats_report_xml",
            "pbreports.tasks.loading_report_xml")
      } else Seq("pbscala::dataset_report")
      val reports = datastore.files.filter(_.fileTypeId == FileTypes.REPORT.fileTypeId)
      reports.size must beEqualTo(REPORT_IDS.size)
      val taskIds = reports.map(_.sourceId).sorted
      taskIds must beEqualTo(REPORT_IDS)
    }
  }
}

// XXX standalone test class for debugging runtime issues - not intended to
// be run in standard suite
class DataSetMergerScalingSpec extends Specification with LazyLogging with timeUtils {
  val DATA_DIR = "/unknownpath" // replace me with something useful
  args(skipAll = !Paths.get(DATA_DIR).toFile.exists)

  sequential

  private def listSubreadSetFiles(f: File): Array[File] = {
    f.listFiles.filter((fn) => fn.toString.endsWith(".subreadset.xml")).toArray ++ f.listFiles.filter(_.isDirectory).flatMap(listSubreadSetFiles)
  }

  val writer = new PrinterJobResultsWriter

  "Test merging large numbers of datasets" should {
    "Merge SubreadSets using jobtype API" in {
      val paths = listSubreadSetFiles(Paths.get(DATA_DIR).toFile)
      println(s"Found ${paths.size} SubreadSet XMLs")
      val opts = MergeDataSetOptions(DataSetMetaTypes.Subread.toString,
                                     paths.map(_.toString), "merge_datasets")
      val outputDir = Files.createTempDirectory("merge-job-test")
      val job = JobResource(UUID.randomUUID, outputDir)
      println(s"Merge job output dir is ${outputDir.toString}")
      val j = opts.toJob
      val startedAt = JodaDateTime.now()
      val jobResult = j.run(job, writer)
      jobResult.isRight must beTrue
      val elapsed = computeTimeDeltaFromNow(startedAt)
      println(s"Merge job took $elapsed seconds")
      val datastore = jobResult.right.get.asInstanceOf[PacBioDataStore]
      val REPORT_IDS = if (PbReports.isAvailable()) {
        Seq("pbreports.tasks.adapter_report_xml",
            "pbreports.tasks.control_report",
            "pbreports.tasks.filter_stats_report_xml",
            "pbreports.tasks.loading_report_xml")
      } else Seq("pbscala::dataset_report")
      val reports = datastore.files.filter(_.fileTypeId == FileTypes.REPORT.fileTypeId)
      reports.size must beEqualTo(REPORT_IDS.size)
      val taskIds = reports.map(_.sourceId).sorted
      taskIds must beEqualTo(REPORT_IDS)
    }
  }
}