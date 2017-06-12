import org.specs2.mutable.Specification
import java.time.LocalDateTime


class HelloWorldIntegrationSpec extends Specification {
  "HelloWorld Integration test" should {
    "add two numbers" in {
      val x = LocalDateTime.now()
      println(s"Starting to run test at $x")
      Thread.sleep(2000)
      val n = LocalDateTime.now()
      println(s"Ending test at $n")
      1 === 1
    }
  }
}