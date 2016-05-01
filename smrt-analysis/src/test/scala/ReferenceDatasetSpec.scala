import java.nio.file.{Paths, Files}
import com.pacbio.secondary.analysis.converters.ReferenceInfoConverter._
import com.pacbio.secondary.analysis.datasets.ReferenceDataset
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.legacy.{ReferenceInfoUtils, ReferenceEntry}
import com.pacbio.secondary.analysis.tools.{ReferenceInfoToDataSetTool, ReferenceConverterConfig}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

import com.pacificbiosciences.pacbiodatasets.ReferenceSet
import com.pacbio.secondary.analysis.datasets.io.ImplicitDataSetLoader._
import com.pacbio.secondary.analysis.datasets.validators.ImplicitDataSetValidators._

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
    "Convert ReferenceEntry to ReferenceDataSetIO" in {
      val resource = "/reference-infos/ecoli_reference.info.xml"
      val path = getClass.getResource(resource)
      val e = ReferenceEntry.loadFrom(path)
      val rio = converter(e)
      rio.dataset.organism must beEqualTo(e.record.organism)
    }
    "Successfully parse a Reference Dataset XML file" in {
      val resource = "/dataset-references/example_reference_dataset/reference.dataset.xml"
      val url = getClass.getResource(resource)
      val d = DataSetLoader.loadReferenceSet(Paths.get(url.getPath))
      d.getDataSetMetadata.getNumRecords must beEqualTo(6)
    }
    "Reference Into -> ReferenceSet end-to-end" in {
      val resource = "/reference-infos/ecoli_reference.info.xml"
      val path = getClass.getResource(resource)
      val outputDataSetXML = Files.createTempFile("", "reference.dataset.xml")
      val conf = ReferenceConverterConfig(Paths.get(path.toURI).toAbsolutePath.toString, outputDataSetXML.toAbsolutePath.toString)
      logger.info(s"Config $conf")
      val result = ReferenceInfoToDataSetTool.run(conf)
      result.isRight must beTrue
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
