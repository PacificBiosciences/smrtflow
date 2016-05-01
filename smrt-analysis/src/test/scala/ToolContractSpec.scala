import java.nio.file.{Files, Paths}
import com.pacbio.secondary.analysis.contracts.ContractLoaders
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import spray.json._
import com.pacbio.secondary.analysis.jobs.SecondaryJobJsonProtocol

/**
 * Test for all pipeline related specs
 * Created by mkocher on 8/18/15.
 */
class ToolContractSpec extends Specification with SecondaryJobJsonProtocol with LazyLogging{

  sequential

  val name = "tool-contracts/dev_example_dev_txt_app_tool_contract.avro"

  "Loading avro tool contract" should {
    "Smoke test to load a pbocommand dev test Avro file" in {
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)
      true must beEqualTo(Files.exists(p))
    }
  }
  "Test resolving tool contract" should {
    "Smoke test for loading pbcommand Tool Contract" in {
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)

      val tc = ContractLoaders.loadToolContract(p)
      logger.info(tc.toString)
      tc.getToolContract.getIsDistributed must beEqualTo(false)
    }
  }
}
