import org.specs2.mutable.Specification

import java.nio.file.Paths

import com.pacbio.secondaryinternal.IOUtils

class IOUtilsSpec extends Specification {

  def loadResource(name: String) = {
    val x = getClass.getResource(name)
    Paths.get(x.toURI)
  }
  val xs =
    """condId,host,jobId
      |a,smrtlink-a:9999,1
      |a,smrtlink-b,2
      |b,smrtlink-c,3""".stripMargin


  "Sanity CSV parsing test" should {
    "Parse a CSV with 3 conditions" in {

      val p = loadResource("conditions-01.csv")
      val records = IOUtils.parseConditionCsv(p)

      records.length must beEqualTo(3)
    }
    "Parse CSV string" in {
      println(xs)
      val records = IOUtils.parseConditionCsv(scala.io.Source.fromString(xs))
      println(records)
      records.length must beEqualTo(3)
      // Check one example with port parsing
      records(0).id must beEqualTo("a")
      records(0).host must beEqualTo("smrtlink-a")
      records(0).port must beEqualTo(9999)
      records(0).jobId must beEqualTo(1)
      // Check one example with default port
      records(2).id must beEqualTo("b")
      records(2).host must beEqualTo("smrtlink-c")
      records(2).port must beEqualTo(8081)
      records(2).jobId must beEqualTo(3)
    }
  }

}