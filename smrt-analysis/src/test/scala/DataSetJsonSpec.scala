
import java.nio.file.{Paths, Files}

import com.pacbio.secondary.analysis.datasets.io.{DataSetJsonUtils,DataSetLoader,DataSetJsonProtocol}
import com.pacificbiosciences.pacbiodatasets._

import spray.json._

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification

class DataSetJsonSpec extends Specification with LazyLogging{
  import DataSetJsonProtocol._

  def getPath(resource: String) = Paths.get(getClass.getResource(resource).getPath)

  "SubreadSet" should {
    val path = getPath("/dataset-subreads/m54008_160215_180009.subreadset.xml")
    val d = DataSetLoader.loadSubreadSet(path)
    "Convert to and from JSON" in {
      val j = DataSetJsonUtils.subreadSetToJson(d)
      val d2 = DataSetJsonUtils.subreadSetFromJson(j)
      d2.getUniqueId must beEqualTo(d.getUniqueId)
      d2.getExternalResources.getExternalResource.size must beEqualTo(1)
    }
    "Use implicit conversions to and from JSON" in {
      val j = d.toJson
      val d2 = j.convertTo[SubreadSet]
      d2.getUniqueId must beEqualTo(d.getUniqueId)
      d2.getExternalResources.getExternalResource.size must beEqualTo(1)
    }
  }
  "ReferenceSet" should {
    val path = getPath("/dataset-references/example_reference_dataset/reference.dataset.xml")
    val d = DataSetLoader.loadReferenceSet(path)
    "Convert to and from JSON" in {
      val j = DataSetJsonUtils.referenceSetToJson(d)
      val d2 = DataSetJsonUtils.referenceSetFromJson(j)
      d2.getUniqueId must beEqualTo(d.getUniqueId)
    }
    "Use implicit conversions to and from JSON" in {
      val j = d.toJson
      val d2 = j.convertTo[ReferenceSet]
      d2.getUniqueId must beEqualTo(d.getUniqueId)
    }
  }
  "HdfSubreadSet" should {
    val path = getPath("/dataset-hdfsubreads/m150404_101626_42267_c100807920800000001823174110291514_s1_p0.hdfsubread.dataset.xml")
    val d = DataSetLoader.loadHdfSubreadSet(path)
    "Convert to and from JSON" in {
      val j = DataSetJsonUtils.hdfSubreadSetToJson(d)
      val d2 = DataSetJsonUtils.hdfSubreadSetFromJson(j)
      d2.getUniqueId must beEqualTo(d.getUniqueId)
    }
    "Use implicit conversions to and from JSON" in {
      val j = d.toJson
      val d2 = j.convertTo[HdfSubreadSet]
      d2.getUniqueId must beEqualTo(d.getUniqueId)
    }
  }
  "AlignmentSet" should {
    val path = getPath("/dataset-alignments/tiny_ds_lambda.alignmentset.xml")
    val d = DataSetLoader.loadAlignmentSet(path)
    "Convert to and from JSON" in {
      val j = DataSetJsonUtils.alignmentSetToJson(d)
      val d2 = DataSetJsonUtils.alignmentSetFromJson(j)
      d2.getUniqueId must beEqualTo(d.getUniqueId)
    }
    "Use implicit conversions to and from JSON" in {
      val j = d.toJson
      val d2 = j.convertTo[AlignmentSet]
      d2.getUniqueId must beEqualTo(d.getUniqueId)
    }
  }
  "GmapReferenceSet" should {
    val path = getPath("/dataset-gmap-references/example_01.xml")
    val d = DataSetLoader.loadGmapReferenceSet(path)
    "Convert to and from JSON" in {
      val j = DataSetJsonUtils.gmapReferenceSetToJson(d)
      val d2 = DataSetJsonUtils.gmapReferenceSetFromJson(j)
      d2.getUniqueId must beEqualTo(d.getUniqueId)
    }
    "Use implicit conversions to and from JSON" in {
      val j = d.toJson
      val d2 = j.convertTo[GmapReferenceSet]
      d2.getUniqueId must beEqualTo(d.getUniqueId)
    }
  }
}
