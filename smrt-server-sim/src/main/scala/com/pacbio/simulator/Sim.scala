package com.pacbio.simulator

import java.io.PrintWriter
import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.simulator.scenarios._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.format.ISODateTimeFormat
import resource._
import scopt.OptionParser

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.xml._

object Sim extends App with LazyLogging {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val system: ActorSystem = ActorSystem("sim")

  final val VERSION = "0.1.0"
  final val TOOL_ID = "scenario-runner"

  // Add new scenario loaders here
  final val LOADERS: Map[String, ScenarioLoader] = Map(
    "ExampleScenario" -> ExampleScenarioLoader,
    "RunDesignScenario" -> RunDesignScenarioLoader,
    "DataSetScenario" -> DataSetScenarioLoader,
    "ProjectsScenario" -> ProjectsScenarioLoader,
    "PbsmrtpipeScenario" -> PbsmrtpipeScenarioLoader,
    "StressTest" -> StressTestScenarioLoader,
    "RunDesignWithICSScenario" -> RunDesignWithICSScenarioLoader,
    "UpgradeScenario" -> UpgradeScenarioLoader,
    "ChemistryBundleScenario" -> ChemistryBundleScenarioLoader,
    "LargeMergeScenario" -> LargeMergeScenarioLoader,
    "TechSupportScenario" -> TechSupportScenarioLoader,
    "DbBackUpScenario" -> DbBackUpScenarioLoader,
    "SampleNamesScenario" -> SampleNamesScenarioLoader
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

  val simArgs: SimArgs = parser.parse(args, SimArgs()).getOrElse {
    System.exit(1)
    null // unreachable, but required for return type
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

  var scenario: Scenario = null
  var result: ScenarioResult = null
  var succeeded: Boolean = false
  try {
    // Construct scenario
    val config = simArgs.config.map { p =>
      ConfigFactory.parseFile(p.toFile).resolve()
    }

    config.foreach { c =>
      import scala.collection.JavaConversions.asScalaSet
      println("\nConfigs:")
      c.entrySet().foreach { e =>
        println(s"${e.getKey}: ${e.getValue.render()}")
      }
      println()
    }
    scenario = simArgs.loader.load(config)

    // run scenario
    val f = Future { scenario.setUp() }
      .flatMap { _ =>
        scenario.run()
      }
      .andThen { case _ => scenario.tearDown() }
    result = Await.result(f, simArgs.timeout)
    succeeded = result.stepResults.forall(_.result.succeeded)

    // output results
    simArgs.outputXML.foreach {
      outputXML(result, _, scenario.requirements)
    }

  } finally {
    system.shutdown()
  }

  if (succeeded) System.exit(0) else System.exit(1)
}
