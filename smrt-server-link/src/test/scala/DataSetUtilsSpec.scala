
import java.nio.file.{Files, Path, Paths}

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetadataUtils,
  DataSetUpdateUtils
}
//import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData


class DataSetUtilsSpec
    extends Specification 
    with DataSetMetadataUtils
    with LazyLogging {

  private def getPath(name: String) =
    Paths.get(getClass.getResource(name).toURI)

  private def getSubreads(name: String) =
    DataSetLoader.loadSubreadSet(getPath(name))

  "Extract metadata from SubreadSet" should {
    "Get well sample record" in {
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getWellSample(ds).toOption.get.getName === "Well Sample 1"
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getWellSample(ds).failed.get.getMessage === "multiple well sample records are present"
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
      setBioSampleName(ds, "foo").toOption === Some("Set 2 BioSample tag name(s) to foo")
    }
    "Get and set well sample names" in {
      val updateMsg = Some("Set 1 WellSample tag name(s) to foo")
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Well Sample 1"))
      canEditWellSampleName(ds) must beTrue
      setWellSampleName(ds, "foo").toOption === updateMsg
      getWellSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Alice_Sample_1", "Bob Sample 1"))
      canEditWellSampleName(ds) must beFalse
      setWellSampleName(ds, "foo").toOption must beNone
      getWellSampleNames(ds) must beEqualTo(Seq("Alice_Sample_1", "Bob Sample 1"))
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
      setWellSampleName(ds, "foo").toOption === Some("Set 2 WellSample tag name(s) to foo")
    }
    "Get DNA Barcode names" in {
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getDnaBarcodeNames(ds) must beEqualTo(Seq.empty[String])
      ds = getSubreads("/dataset-subreads/sample1.subreadset.xml")
      getDnaBarcodeNames(ds) must beEqualTo(Seq("F1--R1"))
      ds = getSubreads("/dataset-subreads/multi_sample.subreadset.xml")
      getDnaBarcodeNames(ds) must beEqualTo(Seq("F1--R1", "F2--R2"))
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getDnaBarcodeNames(ds) must beEqualTo(Seq("F1--R1", "F2--R2"))
      ds = getSubreads("/dataset-subreads/no_collections.subreadset.xml")
      getDnaBarcodeNames(ds) must beEqualTo(Seq.empty[String])
    }
  }

  "Write updated XML files" should {
    "Update well and bio sample names" in {
      var dsFile = getPath("/dataset-subreads/example_01.xml")
      var tmpFile = Files.createTempFile("updated", ".subreadset.xml")
      DataSetUpdateUtils.saveUpdatedCopy(dsFile, tmpFile, resolvePaths = false) // no updates
      var ds = DataSetLoader.loadSubreadSet(tmpFile)
      getWellSampleNames(ds) must beEqualTo(Seq("Well Sample 1"))
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      DataSetUpdateUtils.testApplyEdits(dsFile, Some("foo"), Some("bar")) must beNone
      DataSetUpdateUtils.saveUpdatedCopy(dsFile, tmpFile, Some("foo"), Some("bar"), resolvePaths = false) must beNone
      ds = DataSetLoader.loadSubreadSet(tmpFile)
      getBioSampleNames(ds) must beEqualTo(Seq("foo"))
      getWellSampleNames(ds) must beEqualTo(Seq("bar"))
      DataSetUpdateUtils.saveUpdatedCopy(dsFile, tmpFile, Some(UNKNOWN), Some(MULTIPLE_SAMPLES_NAME), resolvePaths = false) must beNone
      ds = DataSetLoader.loadSubreadSet(tmpFile)
      getWellSampleNames(ds) must beEqualTo(Seq("Well Sample 1"))
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      dsFile = getPath("/dataset-subreads/pooled_sample.subreadset.xml")
      DataSetUpdateUtils.saveUpdatedCopy(dsFile, tmpFile, Some("foo"), Some("bar"), resolvePaths = false) must beNone
      ds = DataSetLoader.loadSubreadSet(tmpFile)
      getBioSampleNames(ds) must beEqualTo(Seq("foo"))
      getWellSampleNames(ds) must beEqualTo(Seq("bar"))
      // failure mode
      dsFile = getPath("/dataset-subreads/no_collections.subreadset.xml")
      val ERR = Some("Error(s) occurred applying metadata updates: no well sample records are present; no well sample records are present")
      DataSetUpdateUtils.saveUpdatedCopy(dsFile, tmpFile, Some("foo"), Some("bar"), resolvePaths = false) === ERR
      DataSetUpdateUtils.testApplyEdits(dsFile, Some("foo"), Some("bar")) === ERR
      ds = DataSetLoader.loadSubreadSet(tmpFile)
      getWellSampleNames(ds) must beEqualTo(Seq.empty[String])
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      dsFile = getPath("/dataset-subreads/multi_sample.subreadset.xml")
      DataSetUpdateUtils.saveUpdatedCopy(dsFile, tmpFile, Some("foo"), Some("bar"), resolvePaths = false) === Some("Error(s) occurred applying metadata updates: Multiple unique BioSample names already present")
    }
  }
}
