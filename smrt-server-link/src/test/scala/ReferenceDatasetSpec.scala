import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.datasets.io.ImplicitDataSetLoader._
import com.pacbio.secondary.smrtlink.analysis.datasets.validators.ImplicitDataSetValidators._
import com.pacificbiosciences.pacbiodatasets.ReferenceSet
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

/**
 * Created by mkocher on 3/10/15.
 *
 * Spec for Reference DataSet parsing and writing
 *
 * Performs and end-to-end test for ReferenceInfoXML -> ReferenceDataSet XML
 */

class ReferenceDatasetSpec extends Specification with LazyLogging{

  sequential

  "Reference Dataset" should {
    "Successfully parse a Reference Dataset XML file" in {
      val resource = "/dataset-references/example_reference_dataset/reference.dataset.xml"
      val url = getClass.getResource(resource)
      val d = DataSetLoader.loadReferenceSet(Paths.get(url.getPath))
      d.getDataSetMetadata.getNumRecords must beEqualTo(6)
    }
    "Successfully load and validate a Reference Dataset XML file" in {
      val resource = "/dataset-references/example_reference_dataset/reference.dataset.xml"
      val url = getClass.getResource(resource)
      val px = Paths.get(url.getPath)
      // This needs to have the files resolved
      val ds = loaderAndResolve[ReferenceSet](px)
      val ex = validator(ds)
      logger.info(s"Validated ReferenceSet $ex")
      val d = DataSetLoader.loadReferenceSet(Paths.get(url.getPath))
      d.getDataSetMetadata.getNumRecords must beEqualTo(6)
    }
  }
}
