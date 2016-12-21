import java.nio.file.{Files, Paths}
import com.pacbio.secondary.analysis.contracts.ContractLoaders
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import spray.json._
import com.pacbio.secondary.analysis.jobs.SecondaryJobJsonProtocol

import collection.JavaConversions._
import collection.JavaConverters._

/**
 * Test for all pipeline related specs
 * Created by mkocher on 8/18/15.
 */
class ToolContractSpec extends Specification with SecondaryJobJsonProtocol with LazyLogging{

  sequential

  val name = "resolved-tool-contracts/smrtflow.tasks.example_tool_resolved_tool_contract.avro"

  "Loading avro tool contract" should {
    "Smoke test to load a pbcommand dev test Avro file" in {
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)
      true must beEqualTo(Files.exists(p))
    }
  }
  "Test resolving tool contract" should {
    "Smoke test for loading Resolved Tool Contract" in {

      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)

      val rtc = ContractLoaders.loadResolvedToolContract(p)

      //logger.info(tc.toString)
      rtc.getResolvedToolContract.getIsDistributed must beEqualTo(false)
      rtc.getResolvedToolContract.getInputFiles.length must beEqualTo(1)
    }
  }
}
