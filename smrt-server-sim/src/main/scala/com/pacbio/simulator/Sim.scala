package com.pacbio.simulator

import java.io.PrintWriter
import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import com.pacbio.simulator.scenarios._
import com.typesafe.config.ConfigFactory
import org.joda.time.format.ISODateTimeFormat
import resource._
import scopt.OptionParser

import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.duration._

object Sim extends App {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val system: ActorSystem = ActorSystem("sim")

  final val VERSION = "0.1.0"
  final val TOOL_ID = "scenario-runner"

  // Add new scenario loaders here
  final val LOADERS: Map[String, ScenarioLoader] = Map(
    "ExampleScenario"   -> ExampleScenarioLoader,
    "RunDesignScenario" -> RunDesignScenarioLoader,
    "DataSetImportScenario" -> DataSetImportScenarioLoader,
    "ProjectsScenario" -> ProjectsScenarioLoader,
    "StressTest" -> StressTestScenarioLoader
  )

  final val DESCRIPTION =
    """Runs simulator scenarios.
      |
      |Includes the following scenario loaders:
    """.stripMargin + "\n" + LOADERS.keys.map(name => s" - $name\n").reduce(_ + _)

  val parser = new OptionParser[SimArgs](TOOL_ID) {
    head(s"Simulator Scenario Runner v$VERSION")
    note(DESCRIPTION)

    arg[String]("loader") valueName "<loader>" action { (v, c) =>
      c.copy(loader = LOADERS(v))
    } text "Name of the scenario loader to use. (E.g. ExampleScenario)"

    arg[String]("config-file") valueName "<file>" action { (v, c) =>
      c.copy(config = Some(Paths.get(v)))
    } optional() text "Path to a config file. (E.g.: /etc/pacbio/example.conf) " +
      "Optional, but may be required by the scneario loader."

    opt[String]('o', "output-xml") valueName "<file>" action { (v, c) =>
      c.copy(outputXML = Some(Paths.get(v)))
    } text "Optional output file for Jenkins-readable JUnit-style XML"

    opt[String]('t', "time-out") valueName "<duration>" action { (v, c) =>
      c.copy(timeout = Duration(v))
    } text "Optional timeout per scenario, as text (default = 15 minutes)"
  }

  val simArgs: SimArgs = parser.parse(args, SimArgs()).getOrElse {
    System.exit(1)
    null // unreachable, but required for return type
  }

  def outputXML(result: ScenarioResult, outputPath: Path): Unit = {
    import StepResult._

    def millisToSecs(m: Long): Double = Duration(m, MILLISECONDS).toUnit(SECONDS)

    val builder = new StringBuilder()
    builder.append("<testsuites>\n")
    val steps = result.stepResults
    val scenarioName = result.name
    val tests = steps.size
    val failures = steps.count(!_.result.succeeded)
    val timestamp = ISODateTimeFormat.dateTime().print(result.timestamp)
    val runTimeSecs = millisToSecs(result.runTimeMillis)
    builder.append(
      s"  <testsuite name='$scenarioName' " +
        s"tests='$tests' " +
        s"failures='$failures' " +
        s"time='$runTimeSecs' " +
        s"timestamp='$timestamp'>\n")
    steps.indices.foreach { i =>
      val step = steps(i)
      val stepName = f"${i+1}%04d-${step.name}"
      val className = s"Simulator.$scenarioName"
      val stepRunTimeSecs = millisToSecs(step.runTimeMillis)
      builder.append(s"    <testcase name='$stepName' classname='$className' time='$stepRunTimeSecs'")
      step.result match {
        case SUCCEEDED => builder.append(" />\n")
        case SUPPRESSED | SKIPPED =>
          builder.append(">\n")
          builder.append(s"      <skipped />\n")
          builder.append(s"    </testcase>\n")
        case FAILED(sm, lm) =>
          builder.append(">\n")
          builder.append(s"      <failure message='$sm'>\n")
          builder.append(s"$lm\n")
          builder.append(s"      </failure>\n")
          builder.append(s"    </testcase>\n")
        case EXCEPTION(sm, lm) =>
          builder.append(">\n")
          builder.append(s"      <error message='$sm'>\n")
          builder.append(s"$lm\n")
          builder.append(s"      </error>\n")
          builder.append(s"    </testcase>\n")
      }
    }
    builder.append("  </testsuite>\n")
    builder.append("</testsuites>\n")

    for (output <- managed(new PrintWriter(outputPath.toFile))) {
      output.print(builder.mkString)
    }
  }

  // EXECUTION

  var scenario: Scenario = null
  var result: ScenarioResult = null
  var succeeded: Boolean = false
  try {
    // Construct scenario
    val config = simArgs.config.map{p => ConfigFactory.parseFile(p.toFile).resolve()}

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
    val f = Future { scenario.setUp() }.flatMap { _ => scenario.run() }.andThen { case _ => scenario.tearDown() }
    result = Await.result(f, simArgs.timeout)
    succeeded = result.stepResults.forall(_.result.succeeded)

    // output results
    simArgs.outputXML.foreach {
      outputXML(result, _)
    }

  } finally {
    system.shutdown()
  }

  if (succeeded) System.exit(0) else System.exit(1)
}
