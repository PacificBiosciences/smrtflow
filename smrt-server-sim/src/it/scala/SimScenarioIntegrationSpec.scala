import java.nio.file.{Files, Path}

import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.analysis.externaltools.{ExternalCmdFailure, ExternalToolsUtils, PacBioTestData}
import com.pacbio.simulator.ScenarioConstants
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.specs2.mutable.Specification

/**
  * I'm not completely convinced that this should be exe subprocess calls to scenario-runner.
  * I think direct lib calls would be easier and would centralize the logging and avoid
  * these unnecessary layers of indirection. However, this would leave a gap in the test
  * coverage for scenario-runner.
  *
  * This should replace siv-tests/build_scripts/run_sim_tests.sh
  *
  * Note, 'scenario-runner must be in the path. sbt smrt-server-sim/pack should be called before
  * running the integration tests and the PATH should be set correctly.
  *
  */
class SimScenarioIntegrationSpec extends Specification with ConfigLoader with LazyLogging{

  // This is necessary because some Scenarios will try to re-import the same data from
  // PacBioTestData
  sequential

  val simScenarioConf = Files.createTempFile("sim-scenario", ".conf")
  /**
    * This is an odd interface. The SIM config is NOT loadable from the ConfigLoader instance.
    *  Therefore manually pulling this out and writing
    * @return
    */
  def loadPort() = conf.getInt(ScenarioConstants.PORT)

  def writeScenarioConf(port: Int, host: String = "localhost", output: Path): Path = {
    val sx =
      s"""
        |${ScenarioConstants.PORT} = $port
        |${ScenarioConstants.HOST} = $host
      """.stripMargin
    FileUtils.write(output.toFile, sx)
    logger.info(sx)
    logger.info(s"Wrote sim config to $output")
    output
  }

  def toCmd(scenarioType: String) = Seq("scenario-runner", scenarioType, simScenarioConf.toAbsolutePath.toString,
    "-o", s"sim-${scenarioType.toLowerCase()}_junit.xml",
    "--log-file", s"sim-${scenarioType.toLowerCase()}.log",
    "--log-level", "DEBUG")

  def runScenario(scenarioType: String): Option[ExternalCmdFailure] = {
    val cmd = toCmd(scenarioType)
    println(s"Scenario cmd $cmd")
    ExternalToolsUtils.runSimpleCmd(cmd) match {
      case Some(ex) =>
        println(s"Error running scenario $scenarioType with error $ex")
        Some(ex)
      case _ => None
    }
  }

  step(writeScenarioConf(loadPort(), output = simScenarioConf))

  "Scenario Runners" should {
    "test scenario.conf file was successfully written" in {
      Files.exists(simScenarioConf)
    }
    "scenario-runner exe is in PATH" in {
      ExternalToolsUtils.which("scenario-runner") must beSome
    }
    "DataSet Scenario" in {
      logger.info("Running DataSet Scenario")
      runScenario("DataSetScenario") must beNone
    }
  }
}
