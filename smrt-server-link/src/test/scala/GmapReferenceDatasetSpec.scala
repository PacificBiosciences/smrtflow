import java.nio.file.{Paths, Files}

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

import com.pacificbiosciences.pacbiodatasets.GmapReferenceSet

import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.datasets.io.ImplicitDataSetLoader._
import com.pacbio.secondary.smrtlink.analysis.datasets.validators.ImplicitDataSetValidators._

/**
  * Created by mkocher on 3/10/15.
  *
  * Spec for Reference DataSet parsing and writing
  *
  * Performs and end-to-end test for ReferenceInfoXML -> ReferenceDataSet XML
  */
class GmapReferenceDatasetSpec extends Specification with LazyLogging {

  sequential

  "GMAP Reference Dataset" should {
    "Successfully parse a GMAP Reference Dataset XML file" in {
      val resource = "/dataset-gmap-references/example_01.xml"
      val url = getClass.getResource(resource)
      val d = DataSetLoader.loadGmapReferenceSet(Paths.get(url.getPath))
      d.getDataSetMetadata.getNumRecords must beEqualTo(1)
    }
    "Successfully load and validate a GMAP Reference Dataset XML file" in {
      val resource = "/dataset-gmap-references/example_01.xml"
      val url = getClass.getResource(resource)
      val px = Paths.get(url.getPath)
      // This needs to have the files resolved
      val ds = loaderAndResolve[GmapReferenceSet](px)
      val ex = validator(ds)
      logger.info(s"Validated GmapReferenceSet $ex")
      val d = DataSetLoader.loadGmapReferenceSet(Paths.get(url.getPath))
      d.getDataSetMetadata.getNumRecords must beEqualTo(1)
    }
  }
}
