package com.pacbio.simulator

import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.simulator.scenarios._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.format.ISODateTimeFormat
import resource._
import scopt.OptionParser

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml._

object Sim extends App with LazyLogging {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val system: ActorSystem = ActorSystem("sim")

  final val VERSION = "0.2.0"
  final val TOOL_ID = "scenario-runner"

  // Add new scenario loaders here
  final val LOADERS: Map[String, ScenarioLoader] = Map(
    "ExampleScenario" -> ExampleScenarioLoader,
    "RunDesignScenario" -> RunDesignScenarioLoader,
    "DataSetScenario" -> DataSetScenarioLoader,
    "ProjectsScenario" -> ProjectsScenarioLoader,
    "PbsmrtpipeScenario" -> PbsmrtpipeScenarioLoader,
    "StressTest" -> StressTestScenarioLoader,
    "UpgradeScenario" -> UpgradeScenarioLoader,
    "ChemistryBundleScenario" -> ChemistryBundleScenarioLoader,
    "LargeMergeScenario" -> LargeMergeScenarioLoader,
    "TechSupportScenario" -> TechSupportScenarioLoader,
    "DbBackUpScenario" -> DbBackUpScenarioLoader,
    "MultiAnalysisScenario" -> MultiAnalysisScenarioLoader,
    "SampleNamesScenario" -> SampleNamesScenarioLoader,
    "RegistryScenario" -> RegistryScenarioLoader,
    "CopyDataSetScenario" -> CopyDataSetScenarioLoader
  )

  final val DESCRIPTION =
    """Runs simulator scenarios.
      |
      |Includes the following scenario loaders:
    """.stripMargin + "\n" + LOADERS.keys.toSeq.sorted
      .map(name => s" - $name\n")
      .reduce(_ + _)

  val parser = new OptionParser[SimArgs](TOOL_ID) {
    head(s"Simulator Scenario Runner v$VERSION")
    note(DESCRIPTION)

    arg[String]("loader") valueName "<loader>" action { (v, c) =>
      c.copy(loader = LOADERS(v))
    } text "Name of the scenario loader to use. (E.g. ExampleScenario)"

    arg[String]("config-file") valueName "<file>" action { (v, c) =>
      c.copy(config = Some(Paths.get(v)))
    } optional () text "Path to a config file. (E.g.: /etc/pacbio/example.conf) " +
      "Optional, but may be required by the scenario loader."

    opt[String]('o', "output-xml") valueName "<file>" action { (v, c) =>
      c.copy(outputXML = Some(Paths.get(v)))
    } text "Optional output file for Jenkins-readable JUnit-style XML"

    opt[String]('t', "time-out") valueName "<duration>" action { (v, c) =>
      c.copy(timeout = Duration(v))
    } text "Optional timeout per scenario, as text (default = 15 minutes)"

    // This will handling the adding the logging specific options (e.g., --debug) as well as logging configuration setup
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  def outputXML(result: ScenarioResult,
                outputPath: Path,
                requirements: Seq[String]): Unit = {
    import StepResult._

    def millisToSecs(m: Long): Double =
      Duration(m, MILLISECONDS).toUnit(SECONDS)

    val steps = result.stepResults
    val scenarioName = result.name
    val tests = steps.size
    val failures = steps.count(!_.result.succeeded)
    val timestamp = ISODateTimeFormat.dateTime().print(result.timestamp)
    val runTimeSecs = millisToSecs(result.runTimeMillis)
    logger.info(s"Ran ${steps.length} Steps.")

    def stepToXml(klassName: String,
                  stepName: String,
                  stepRunTimeSecs: Double,
                  stepResult: StepResult) = {
      <testcase
      name={stepName}
      classname={klassName}
      time={stepRunTimeSecs.toString} >
        {stepResult.result match {
        case SUCCEEDED => NodeSeq.Empty
        case SUPPRESSED | SKIPPED => <skipped/>
        case FAILED(sm, lm) => <failure message={sm}>{lm}</failure>
        case EXCEPTION(sm, lm) => <error message={sm}>{lm}</error>
      }
        }
      </testcase>
    }

    def stepsToXml(stepResults: Seq[StepResult]) = {
      stepResults.zipWithIndex.map {
        case (stepResult, i) =>
          val stepName = f"${i + 1}%04d-${stepResult.name}"
          val className = s"Simulator.$scenarioName"
          val stepRunTimeSecs = millisToSecs(stepResult.runTimeMillis)
          stepToXml(className, stepName, stepRunTimeSecs, stepResult)
      }
    }

    def requirementsToXml(requirements: Seq[String]) = {
      <properties>
        {requirements.map { req => <property name="Requirement" value={req}/>} }
      </properties>
    }

    val testsuites = {
      <testsuites>
        <testsuite
          name={scenarioName}
          tests={tests.toString}
          failures={failures.toString}
          time={runTimeSecs.toString}
          timestamp={timestamp.toString} >
          { stepsToXml(steps) ++ requirementsToXml(requirements)}
        </testsuite>
      </testsuites>
    }

    scala.xml.XML.save(outputPath.toString, testsuites)
  }

  // EXECUTION
  def writeOutput(result: ScenarioResult,
                  requirements: Seq[String],
                  output: Path): Future[Unit] = {
    Future.successful(outputXML(result, output, requirements))
  }

  def parseArgs(rawArgs: Seq[String]): Try[SimArgs] = {
    Try(
      parser
        .parse(rawArgs, SimArgs())
        .getOrElse(throw new Exception(s"Failed to parse options $rawArgs")))
  }

  def printConfig(config: Config): Unit = {
    import scala.collection.JavaConverters._
    println("\nConfigs:")
    config.entrySet().asScala.foreach { e =>
      println(s"\t${e.getKey}: ${e.getValue.render()}")
    }
    println()
  }

  def printAndLog(sx: String): String = {
    logger.info(sx)
    println(sx)
    sx
  }

  /**
    * Run the loaded Scenario and write the output (if provided in the Sim args)
    *
    * @param scenario Loaded Scenario
    * @param simArgs  Sim Args
    * @return
    */
  def runScenario(scenario: Scenario,
                  simArgs: SimArgs): Try[(Boolean, String)] = {
    val f = for {
      _ <- Future.fromTry(Try(scenario.setUp()))
      _ <- Future.successful(
        printAndLog(s"Attempting to run scenario ${scenario.name}"))
      result <- scenario.run()
      _ <- simArgs.outputXML
        .map(p => writeOutput(result, scenario.requirements, p))
        .getOrElse(Future.successful(Unit))
      wasSuccessful <- Future.successful(
        result.stepResults.forall(_.result.succeeded))
    } yield
      (wasSuccessful,
       s"Completed running ${scenario.name} was successful? $wasSuccessful")

    // We always call tear down
    val fx = f.andThen { case _ => Try(scenario.tearDown()) }

    printAndLog(
      s"Starting to run scenario ${scenario.name} with timeout ${simArgs.timeout}")
    Try(Await.result(fx, simArgs.timeout))
  }

  def runScenarioFromArgs(simArgs: SimArgs): Try[(Boolean, String)] = {

    for {
      config <- Try(
        simArgs.config.map(p => ConfigFactory.parseFile(p.toFile).resolve()))
      _ <- Try(config.map(printConfig).getOrElse(Unit))
      scenario <- Try(simArgs.loader.load(config))
      _ <- Try(printAndLog(s"Successfully loaded $scenario"))
      (wasSuccessful, msg) <- runScenario(scenario, simArgs)
    } yield (wasSuccessful, msg)
  }

  private def exitError(msg: String) = {
    logger.error(msg)
    System.err.println(msg + "\n")
    system.terminate()
    System.exit(1)
  }

  /// Run Main
  parseArgs(args).flatMap(runScenarioFromArgs) match {
    case Success((wasSuccessful, msg)) =>
      if (!wasSuccessful) {
        val exitMsg = s"Scenario failed.  Exiting with exit code 1."
        exitError(exitMsg)
      } else {
        println(
          "Successfully completed running scenario. Exiting with exit code 0.")
        System.exit(0)
      }
    case Failure(ex) =>
      val msg = s"Failed to run scenario. Error $ex\nExiting with exit code 1."
      exitError(msg)
  }

}
