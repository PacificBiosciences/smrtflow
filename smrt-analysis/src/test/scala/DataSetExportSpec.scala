
import java.nio.file.{Files, Paths}

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.analysis.datasets.io.ExportDataSets
import com.pacbio.secondary.analysis.datasets._
import com.pacbio.secondary.analysis.constants.FileTypes


class DataSetExportSpec extends Specification with LazyLogging {

  sequential

  "Export Dataset" should {
    "Generate ZIP file from valid ReferenceSet" in {
      val resource = "/dataset-references/example_reference_dataset/reference.dataset.xml"
      val url = getClass.getResource(resource)
      val datasets = Seq(Paths.get(url.getPath))
      val zipPath = Files.createTempFile("referencesets", ".zip")
      //val zipPath = Paths.get("referencesets.zip")
      val n = ExportDataSets(datasets, FileTypes.DS_REFERENCE.fileTypeId, zipPath)
      n must beGreaterThan(0)
    }
  }
}

// XXX I don't think it's possible to conditionally skip just part of a spec
class DataSetExportSpecAdvanced extends Specification with LazyLogging {
  args(skipAll = !PacBioTestData.isAvailable)

  sequential

  "Export Datasets from PacBioTestData" should {
    "Generate ZIP file from multiple SubreadSets" in {
      val pbdata = PacBioTestData()
      val datasets = Seq(pbdata.getFile("subreads-sequel"),
                         pbdata.getFile("subreads-xml"))
      val zipPath = Files.createTempFile("subreadsets", ".zip")
      //val zipPath = Paths.get("subreadsets.zip")
      val n = ExportDataSets(datasets, FileTypes.DS_SUBREADS.fileTypeId, zipPath)
      n must beGreaterThan(0)
    }
  }
}
