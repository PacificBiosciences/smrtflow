
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.smrtlink.models.{Converters, DataSetMetadataUtils}


class DataSetConvertersSpec extends Specification with DataSetMetadataUtils with LazyLogging {

  private def getPath(name: String) =
    Paths.get(getClass.getResource(name).toURI)

  private def getSubreads(name: String) =
    DataSetLoader.loadSubreadSet(getPath(name))

  "Extract metadata from SubreadSet" should {
    "Get biological sample names" in {
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
      ds = getSubreads("/dataset-subreads/sample1.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice"))
      ds = getSubreads("/dataset-subreads/multi_sample.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice", "Bob"))
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq("Alice", "Bob"))
      ds = getSubreads("/dataset-subreads/no_collections.subreadset.xml")
      getBioSampleNames(ds) must beEqualTo(Seq.empty[String])
    }
    "Get well sample names" in {
      var ds = getSubreads("/dataset-subreads/example_01.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Well Sample 1"))
      ds = getSubreads("/dataset-subreads/merged.dataset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Alice_Sample_1", "Bob Sample 1"))
      ds = getSubreads("/dataset-subreads/multi_sample.subreadset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq("Alice_Bob_Pooled"))
      ds = getSubreads("/dataset-subreads/no_collections.subreadset.xml")
      getWellSampleNames(ds) must beEqualTo(Seq.empty[String])
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

  private def convertSubreads(name: String) = {
    val p = getPath(name)
    val ds = DataSetLoader.loadSubreadSet(p)
    Converters.convert(ds, p, None, 1, 1)
  }

  "Convert datasets to Scala services models" should {
    "SubreadSet" in {
      val sds = convertSubreads("/dataset-subreads/example_01.xml")
      sds.wellSampleName === "Well Sample 1"
      sds.bioSampleName === Converters.UNKNOWN
      sds.wellName === "B01"
      sds.cellIndex === 0
      sds.cellId === "100480560100000001823075906281381"
      sds.runName === "beta4_130726_biotin_DEV_vs_MFG_PB11K_9458p"
      sds.version === "3.0.1"
      sds.tags === "barcode moreTags mapping mytags"
      sds.instrumentControlVersion === "2.3.0.0.140640"
      sds.totalLength === 500000
      sds.numRecords === 500
      sds.dnaBarcodeName === None
    }
    "SubreadSet with no biological samples" in {
      val sds = convertSubreads("/dataset-subreads/m54008_160215_180009.subreadset.xml")
      sds.bioSampleName === Converters.UNKNOWN
      sds.wellSampleName === "dry_D01"
      sds.dnaBarcodeName must beNone
    }
    "SubreadSet with biological sample" in {
      val sds = convertSubreads("/dataset-subreads/sample1.subreadset.xml")
      sds.bioSampleName === "Alice"
      sds.wellSampleName === "Alice_Sample_1"
      sds.dnaBarcodeName === Some("F1--R1")
      sds.runName must beEqualTo("Alice_Bob")
    }
    "SubreadSet with multiple samples" in {
      val sds = convertSubreads("/dataset-subreads/multi_sample.subreadset.xml")
      sds.bioSampleName === "[multiple]"
      sds.wellSampleName === "Alice_Bob_Pooled"
      sds.dnaBarcodeName === Some("[multiple]")
    }
    "Merged SubreadSet" in {
      val sds = convertSubreads("/dataset-subreads/merged.dataset.xml")
      sds.bioSampleName === "[multiple]"
      sds.wellSampleName === "[multiple]"
      sds.dnaBarcodeName === Some("[multiple]")
      sds.runName must beEqualTo("Alice_Bob")
    }
    "SubreadSet with no collection metadata" in {
      val sds = convertSubreads("/dataset-subreads/no_collections.subreadset.xml")
      sds.bioSampleName === Converters.UNKNOWN
      sds.wellSampleName === Converters.UNKNOWN
      sds.dnaBarcodeName must beNone
    }
  }
}

class DataSetConvertersAdvancedSpec extends Specification with LazyLogging {

  args(skipAll = !PacBioTestData.isAvailable)

  sequential

  "Convert PacBioTestData datasets" should {
    "Convert all SubreadSets" in {
      PacBioTestData().getFilesByType(FileTypes.DS_SUBREADS).map { p =>
        val ds = DataSetLoader.loadSubreadSet(p)
        val sds = Converters.convert(ds, p, None, 1, 1)
      }.size must beGreaterThan(0)
    }
    "Convert all HdfSubreadSets" in {
      PacBioTestData().getFilesByType(FileTypes.DS_HDF_SUBREADS).map { p =>
        val ds = DataSetLoader.loadHdfSubreadSet(p)
        val sds = Converters.convert(ds, p, None, 1, 1)
      }.size must beGreaterThan(0)
    }
  }
}
