import org.specs2.mutable.Specification
import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging


// This is added to test if the Unit tests are run
// before the integration tests
class SimSpec extends Specification with LazyLogging{
  "HelloWorld Integration test" should {
    "add two numbers" in {
      1 === 1
    }
  }
}