import org.specs2.mutable.Specification
import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging


class HelloWorldIntegrationSpec extends Specification with LazyLogging{
  "HelloWorld Integration test" should {
    "add two numbers" in {
      val x = LocalDateTime.now()
      logger.info(s"Starting to run test at $x")
      Thread.sleep(2000)
      val n = LocalDateTime.now()
      logger.info(s"Ending test at $n")
      1 === 1
    }
  }
}