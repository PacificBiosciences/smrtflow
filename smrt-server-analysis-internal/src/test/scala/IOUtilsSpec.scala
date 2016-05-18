import org.specs2.mutable.Specification

import java.nio.file.Paths

import com.pacbio.secondaryinternal.models._
import com.pacbio.secondaryinternal.IOUtils

class IOUtilsSpec extends Specification {

  def loadResource(name: String) = {
    val x = getClass.getResource(name)
    Paths.get(x.toURI)
  }

  "Sanity CSV parsing test" should {
    "Parse a CSV with 3 conditions" in {

      val p = loadResource("conditions-01.csv")
      val records = IOUtils.parseConditionCsv(p)

      records.length must beEqualTo(3)
    }
  }

}