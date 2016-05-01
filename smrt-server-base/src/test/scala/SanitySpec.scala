import com.pacbio.common.models.ServiceStatus
import org.specs2.mutable._

import java.util.UUID

class SanitySpec extends Specification{

  "The hello world serialization" should {
    "contains 11 characters" in {
      "Hello World" must have size 11
    }
  }
  "Example test assertion" should {
    "Example 0 must equal 0" in {
      //color.r must beEqualTo(0)
      0 must beEqualTo(0)
    }
  }
  "Create Service Status" should {
    "ServiceStatus sanity" in {
      val s = ServiceStatus("pacbio.service", "A status message", 1234, UUID.randomUUID(), "1.3.3.7", "test-user")
      s.id must beEqualTo("pacbio.service")
    }
  }

}