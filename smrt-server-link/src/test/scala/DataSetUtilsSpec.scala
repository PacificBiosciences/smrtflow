import java.nio.file.{Files, Path, Paths}

import scala.util.Try
import scala.collection.JavaConverters._

import org.specs2.mutable.Specification
import com.typesafe.scalalogging.LazyLogging

import com.pacificbiosciences.pacbiodatasets._
import com.pacificbiosciences.pacbiobasedatamodel.{
  SupportedFilterNames,
  SupportedFilterOperators
}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetadataUtils,
  DataSetUpdateUtils,
  DataSetFilterUtils,
  DataSetFilterProperty
}
import com.pacificbiosciences.pacbiobasedatamodel.{
  ExternalResource,
  ExternalResources
}
//import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData

class DataSetUtilsSpec
    extends Specification
    with DataSetMetadataUtils
    with DataSetFilterUtils
    with LazyLogging
    with PacBioTestUtils {

  private def getSubreads(name: String) =
    DataSetLoader.loadSubreadSet(getResourcePath(name))

  "Extract metadata from SubreadSet" should {
    "Get well sample record" in {
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getWellSample(ds).toOption.map(_.getName) === Some("Well Sample 1")
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getWellSample(ds).failed.get.getMessage === "multiple well sample records are present"
      val exs =
        DataSetUpdateUtils.getAllExternalResources(ds.getExternalResources)
      exs.length === 2
    }
    "Get and set biological sample names" in {
      val updateMsg = Some("Set 1 BioSample tag name(s) to foo")
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      canEditBioSampleName(ds) must beTrue
      setBioSampleName(ds, "foo").toOption === updateMsg
      getBioSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/sample1.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice"))
      canEditBioSampleName(ds) must beTrue
      setBioSampleName(ds, "foo").toOption === updateMsg
      getBioSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/multi_sample.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice", "Bob"))
      canEditBioSampleName(ds) must beFalse
      setBioSampleName(ds, "foo").toOption must beNone
      getBioSampleNames(ds) must beEqualTo(Seq("Alice", "Bob")) // double-check
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice", "Bob"))
      canEditBioSampleName(ds) must beFalse
      setBioSampleName(ds, "foo").toOption must beNone
      ds = getSubreads("/dataset-subreads/no_collections.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      canEditBioSampleName(ds) must beFalse
      setBioSampleName(ds, "foo").toOption must beNone
      ds = getSubreads("/dataset-subreads/pooled_sample.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice"))
      canEditBioSampleName(ds) must beTrue
      setBioSampleName(ds, "foo").toOption === Some(
        "Set 2 BioSample tag name(s) to foo")
    }
    "Get and set well sample names" in {
      val updateMsg = Some("Set 1 WellSample tag name(s) to foo")
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Well Sample 1"))
      canEditWellSampleName(ds) must beTrue
      setWellSampleName(ds, "foo").toOption === updateMsg
      getWellSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getWellSampleNames(ds) must beEqualTo(
        Seq("Alice_Sample_1", "Bob Sample 1"))
      canEditWellSampleName(ds) must beFalse
      setWellSampleName(ds, "foo").toOption must beNone
      getWellSampleNames(ds) must beEqualTo(
        Seq("Alice_Sample_1", "Bob Sample 1"))
      ds = getSubreads("/dataset-subreads/multi_sample.subreadset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Alice_Bob_Pooled"))
      canEditWellSampleName(ds) must beTrue
      setWellSampleName(ds, "foo").toOption === updateMsg
      getWellSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/no_collections.subreadset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq.empty[String])
      canEditWellSampleName(ds) must beFalse
      setWellSampleName(ds, "foo").toOption must beNone
      ds = getSubreads("/dataset-subreads/pooled_sample.subreadset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Alice Sample 1"))
      canEditWellSampleName(ds) must beTrue
      setWellSampleName(ds, "foo").toOption === Some(
        "Set 2 WellSample tag name(s) to foo")
    }
    "Get DNA Barcode names" in {

      def customTest(sx: String, barcodeNames: Seq[String]) = {
        val ds = getSubreads(sx)
        getDnaBarcodeNames(ds) must beEqualTo(barcodeNames)
      }

      customTest("/dataset-subreads/example_01.xml", Seq.empty[String])
      customTest("/dataset-subreads/sample1.subreadset.xml", Seq("F1--R1"))
      customTest("/dataset-subreads/multi_sample.subreadset.xml",
                 Seq("F1--R1", "F2--R2"))
      customTest("/dataset-subreads/merged.dataset.xml",
                 Seq("F1--R1", "F2--R2"))
      customTest("/dataset-subreads/no_collections.subreadset.xml",
                 Seq.empty[String])
    }
  }

  "Write updated XML files" should {
    "Update well and bio sample names" in {
      var dsFile = getResourcePath("/dataset-subreads/example_01.xml")
      val tmpFile = Files.createTempFile("updated", ".subreadset.xml")
      DataSetUpdateUtils.saveUpdatedCopy(dsFile, tmpFile, resolvePaths = false) // no updates
      var ds = DataSetLoader.loadSubreadSet(tmpFile)
      getWellSampleNames(ds) must beEqualTo(Seq("Well Sample 1"))
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      DataSetUpdateUtils.testApplyEdits(dsFile, Some("foo"), Some("bar")) must beNone
      DataSetUpdateUtils.saveUpdatedCopy(dsFile,
                                         tmpFile,
                                         Some("foo"),
                                         Some("bar"),
                                         resolvePaths = false) must beNone
      ds = DataSetLoader.loadSubreadSet(tmpFile)
      getBioSampleNames(ds) must beEqualTo(Seq("foo"))
      getWellSampleNames(ds) must beEqualTo(Seq("bar"))
      DataSetUpdateUtils.saveUpdatedCopy(dsFile,
                                         tmpFile,
                                         Some(UNKNOWN),
                                         Some(MULTIPLE_SAMPLES_NAME),
                                         resolvePaths = false) must beNone
      ds = DataSetLoader.loadSubreadSet(tmpFile)
      getWellSampleNames(ds) must beEqualTo(Seq("Well Sample 1"))
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      dsFile =
        getResourcePath("/dataset-subreads/pooled_sample.subreadset.xml")
      DataSetUpdateUtils.saveUpdatedCopy(dsFile,
                                         tmpFile,
                                         Some("foo"),
                                         Some("bar"),
                                         resolvePaths = false) must beNone
      ds = DataSetLoader.loadSubreadSet(tmpFile)
      getBioSampleNames(ds) must beEqualTo(Seq("foo"))
      getWellSampleNames(ds) must beEqualTo(Seq("bar"))
      // failure mode
      dsFile =
        getResourcePath("/dataset-subreads/no_collections.subreadset.xml")
      val ERR = Some(
        "Error(s) occurred applying metadata updates: no well sample records are present; no well sample records are present")
      DataSetUpdateUtils.saveUpdatedCopy(dsFile,
                                         tmpFile,
                                         Some("foo"),
                                         Some("bar"),
                                         resolvePaths = false) === ERR
      DataSetUpdateUtils.testApplyEdits(dsFile, Some("foo"), Some("bar")) === ERR
      ds = DataSetLoader.loadSubreadSet(tmpFile)
      getWellSampleNames(ds) must beEqualTo(Seq.empty[String])
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      dsFile = getResourcePath("/dataset-subreads/multi_sample.subreadset.xml")
      DataSetUpdateUtils.saveUpdatedCopy(dsFile,
                                         tmpFile,
                                         Some("foo"),
                                         Some("bar"),
                                         resolvePaths = false) === Some(
        "Error(s) occurred applying metadata updates: Multiple unique BioSample names already present")
    }
  }

  "DataSet Utils Spec" should {
    "Get All External Resources from empty resources" in {
      val e1 = new ExternalResources()
      DataSetUpdateUtils.getAllExternalResources(e1).isEmpty must beTrue
    }
    "Get External resources from null" in {
      val e1: ExternalResources = null
      DataSetUpdateUtils.getAllExternalResources(e1).isEmpty must beTrue
    }
    "Get External Resources from 1 ex" in {
      val e1 = new ExternalResource()
      val ex = new ExternalResources()
      ex.getExternalResource.add(e1)
      DataSetUpdateUtils.getAllExternalResources(ex).length === 1
    }
  }

  "DataSet Parent Spec" should {
    "Set the parent dataset" in {
      val dsFile =
        getResourcePath("dataset-subreads/pooled_sample.subreadset.xml")
      val ds = DataSetLoader.loadSubreadSet(dsFile)
      val ds2 = DataSetLoader.loadSubreadSet(dsFile)
      setParent(ds2, ds)
      val parent = ds2.getDataSetMetadata.getProvenance.getParentDataSet
      parent.getUniqueId === ds.getUniqueId
    }
  }

  "DataSet Filter Spec" should {
    val dsFile =
      getResourcePath("dataset-subreads/pooled_sample.subreadset.xml")
    val filters = Seq(Seq(DataSetFilterProperty("bq", ">=", "0.26"),
                          DataSetFilterProperty("rq", ">=", "0.7")),
                      Seq(DataSetFilterProperty("length", ">=", "1000")))
    "Test data models" in {
      val p1 = DataSetFilterProperty("rq", "gte", "0.7")
      p1.name must beEqualTo(SupportedFilterNames.RQ)
      p1.operator must beEqualTo(SupportedFilterOperators.GTE)
      val p2 = DataSetFilterProperty("rq", ">=", "0.7")
      Try {
        DataSetFilterProperty("bq", "!!!", "0.8")
      }.toOption must beNone
    }
    "Add simple filters to dataset" in {
      val ds = DataSetLoader.loadSubreadSet(dsFile)
      Option(ds.getFilters) must beNone
      addSimpleFilter(ds, "bq", ">=", "0.26")
      val rule = DataSetFilterProperty("rq", ">=", "0.7")
      addSimpleFilter(ds, rule)
      addLengthFilter(ds, 1000)
      Try { addSimpleFilter(ds, "bq", "!!!", "0.8") }.toOption must beNone
      Try { addSimpleFilter(ds, "asdf", "==", "0.8") }.toOption must beNone
      ds.getFilters.getFilter.size must beEqualTo(3)
      clearFilters(ds)
      ds.getFilters.getFilter.size must beEqualTo(0)
    }
    "Add multiple filters" in {
      val ds = DataSetLoader.loadSubreadSet(dsFile)
      Option(ds.getFilters) must beNone
      addFilters(ds, filters)
      val xsdFilters = ds.getFilters.getFilter.asScala.toList
      xsdFilters.size must beEqualTo(2)
      xsdFilters(0).getProperties.getProperty.size must beEqualTo(2)
      xsdFilters(1).getProperties.getProperty.size must beEqualTo(1)
    }
    "Write an updated XML file" in {
      val ds = DataSetLoader.loadSubreadSet(dsFile)
      val dsOut = Files.createTempFile("updated", ".subreadset.xml")
      var tx =
        applyFilters(dsFile,
                     dsOut,
                     filters,
                     Some("My filtered dataset"),
                     false,
                     false)
      tx.toOption must beSome
      val ds2 = DataSetLoader.loadSubreadSet(dsOut)
      ds2.getName must beEqualTo("My filtered dataset")
      ds2.getUniqueId !== ds.getUniqueId
      ds2.getUniqueId === tx.toOption.get.getUniqueId
      // XXX 2018-05-04 (nechols): disabled for now
      //val parent = ds2.getDataSetMetadata.getProvenance.getParentDataSet
      //parent.getUniqueId === ds.getUniqueId
      ds2.getFilters.getFilter.size must beEqualTo(2)
      ds2.getTimeStampedName !== ds.getTimeStampedName
      ds2.getCreatedAt !== ds.getCreatedAt
      (ds2.getTags.split(',').toSet contains "copied") must beTrue
    }
  }
}
