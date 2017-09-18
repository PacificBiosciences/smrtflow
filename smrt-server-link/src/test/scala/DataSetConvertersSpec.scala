
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.models.Converters
import com.pacificbiosciences.pacbiodatasets._


class DataSetConvertersSpec extends Specification with LazyLogging {

  private def getPath(name: String) =
    Paths.get(getClass.getResource(name).toURI)

  "Convert datasets to Scala services models" should {
    "SubreadSet" in {
      val p = getPath("/dataset-subreads/m54008_160215_180009.subreadset.xml")
      val ds = DataSetLoader.loadSubreadSet(p)
      val sds = Converters.convert(ds, p, None, 1, 1)
      sds.bioSampleName === Converters.UNKNOWN
    }
    "SubreadSet with biological sample" in {
      val p = getPath("/dataset-subreads/example_01.xml")
      val ds = DataSetLoader.loadSubreadSet(p)
      val sds = Converters.convert(ds, p, None, 1, 1)
      sds.bioSampleName === "consectetur purus"
    }
    "SubreadSet with multiple biological samples" in {
      val p = getPath("/dataset-subreads/example_01_multi_sample.xml")
      val ds = DataSetLoader.loadSubreadSet(p)
      val sds = Converters.convert(ds, p, None, 1, 1)
      sds.bioSampleName === Converters.MULTIPLE_BSAMPLES_NAME
      sds.wellSampleName === "Well Sample 1"
      sds.wellName === "B01"
      sds.cellIndex === 0
      sds.cellId === "100480560100000001823075906281381"
      sds.runName === "beta4_130726_biotin_DEV_vs_MFG_PB11K_9458p"
      sds.version === "3.0.1"
      sds.tags === "barcode moreTags mapping mytags"
      sds.instrumentControlVersion === "2.3.0.0.140640"
      sds.totalLength === 500000
      sds.numRecords === 500
      sds.dnaBarcodeName === None // FIXME update once XSDs and Java code are updated
    }
  }
}
