import com.pacbio.common.semver.SemVersion
import org.specs2.mutable.Specification

/**
  * Created by mkocher on 2/12/17.
  */
class SemVersionSpec extends Specification{

  val v1 = "1.0.1+sha"
  val v2 = "1.1.2"
  val v3 = "1.2.3"
  val v4 = "4.0.0+rc1"

  val versions = Seq(v3, v4, v1, v2)
  val sortedVersions = Seq(v1, v2, v3, v4)

  "Sanity test for SemVersion" should {
    "Parsable S -> V -> S Identity comparision" in {
      versions.map(SemVersion.fromString).map(_.toSemVerString()) === versions
    }
    "Sort by version" in {

      val vxs = versions.map(SemVersion.fromString)
      implicit val sortBySemVer = SemVersion.orderBySemVersion

      vxs.sorted.map(_.toSemVerString()) === sortedVersions
    }
  }

}
