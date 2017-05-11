import java.nio.file.Paths

import com.pacbio.secondary.analysis.legacy.{ReferenceInfoUtils, ReferenceEntry}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

/**
 * Parsing RS-era reference.info.xml files
 */
class ReferenceEntrySpec extends Specification with LazyLogging{

  sequential

  "Parse lambda reference.info.xml" should {
    "Parse reference with muliple contigs" in {
      val resource = "/reference-infos/ncontigs_reference.info.xml"
      val path = getClass.getResource(resource)
      val e = ReferenceEntry.loadFrom(path)
      e.record.id must beEqualTo("simple3")
      e.record.metadata.nContigs must beEqualTo(3)
    }
    "Ecoli reference sanity" in {
      val resource = "/reference-infos/ecoli_reference.info.xml"
      val uri = getClass.getResource(resource)
      val e = ReferenceEntry.loadFrom(uri)
      val path = Paths.get(uri.toURI)
      logger.info(s"path $path")
      val rinfo = ReferenceInfoUtils.loadFrom(path)
      e.record.id must beEqualTo("Ecoli_K12_MG1655")
      e.record.contigs.length must beEqualTo(1)
    }
  }
}