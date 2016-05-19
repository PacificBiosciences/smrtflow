import org.specs2.mutable.Specification

import java.nio.file.Paths

import com.pacbio.secondaryinternal.models._
import com.pacbio.secondaryinternal.IOUtils

class IOUtilsSpec extends Specification {

  def loadResource(name: String) = {
    val x = getClass.getResource(name)
    Paths.get(x.toURI)
  }
  val xs =
    """condId,host,jobId
      |a,smrtlink-a:8081,1
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
    }
  }

}