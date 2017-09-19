
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetadataUtils
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
    "Get and set biological sample names" in {
      val updateMsg = Some("Set BioSample name to foo")
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      setBioSampleName(ds, "foo").toOption === updateMsg
      getBioSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/sample1.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice"))
      setBioSampleName(ds, "foo").toOption === updateMsg
      getBioSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/multi_sample.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice", "Bob"))
      setBioSampleName(ds, "foo").toOption must beNone
      getBioSampleNames(ds) must beEqualTo(Seq("Alice", "Bob"))
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice", "Bob"))
      setBioSampleName(ds, "foo").toOption must beNone
      ds = getSubreads("/dataset-subreads/no_collections.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      setBioSampleName(ds, "foo").toOption must beNone
    }
    "Get and set well sample names" in {
      val updateMsg = Some("Set WellSample name to foo")
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Well Sample 1"))
      setWellSampleName(ds, "foo").toOption === updateMsg
      getWellSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Alice_Sample_1", "Bob Sample 1"))
      setWellSampleName(ds, "foo").toOption must beNone
      getWellSampleNames(ds) must beEqualTo(Seq("Alice_Sample_1", "Bob Sample 1"))
      ds = getSubreads("/dataset-subreads/multi_sample.subreadset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Alice_Bob_Pooled"))
      setWellSampleName(ds, "foo").toOption === updateMsg
      getWellSampleNames(ds) must beEqualTo(Seq("foo"))
      ds = getSubreads("/dataset-subreads/no_collections.subreadset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq.empty[String])
      setWellSampleName(ds, "foo").toOption must beNone
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
}
