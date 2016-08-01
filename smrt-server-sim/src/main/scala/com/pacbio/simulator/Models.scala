package com.pacbio.simulator

import java.io.{PrintWriter, StringWriter}
import java.nio.file.Path

import com.pacbio.simulator.scenarios.ExampleScenarioLoader
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.duration._

object Models

object SystemConstants {
  val SIMULATOR_VERSION = "0.3.1"
}

object StepResult {
  private def throwableLongMsg(ex: Throwable): String = {
    val sw: StringWriter = new StringWriter()
    val pw: PrintWriter = new PrintWriter(sw)
    ex.printStackTrace(pw)
    s"${ex.getMessage}\n${sw.toString}"
  }

  private def failureLongMsg(msg: String): String = {
    val ex = new Exception(msg)
    val st = ex.getStackTrace.drop(2) // Drop stack trace elems for failureLongMsg and FAILED.apply
    ex.setStackTrace(st)
    throwableLongMsg(ex)
  }

  sealed abstract class Result(val succeeded: Boolean,
                               val shortMsg: String,
                               val longMsg: String,
                               val hidden: Boolean)

  // Step ran and succeeded
  case object SUCCEEDED extends Result(true, "", "", false)

  // Step was not run, but not due to a failure
  case object SUPPRESSED extends Result(true, "", "", true)

  // Step failed
  case class FAILED(sm: String, lm: String) extends Result(false, sm, lm, false)

  // Step threw an exception (this will be generated automatically; no need to try-catch inside the step)
  case class EXCEPTION(sm: String, lm: String) extends Result(false, sm, lm, false)

  // Step was skipped because a previous step failed
  case object SKIPPED extends Result(false, "", "", true)

  object FAILED{
    def apply(msg: String): FAILED = FAILED(msg, failureLongMsg(msg))
  }
  object EXCEPTION {
    def apply(ex: Throwable): EXCEPTION = EXCEPTION(ex.getMessage, throwableLongMsg(ex))
  }
}
case class StepResult(name: String, runTimeMillis: Long, result: StepResult.Result)

case class ScenarioResult(name: String,
                          runTimeMillis: Long,
                          stepResults: Seq[StepResult],
                          timestamp: JodaDateTime)

case class SimArgs(loader: ScenarioLoader = ExampleScenarioLoader,
                   config: Option[Path] = None,
                   outputXML: Option[Path] = None,
                   timeout: Duration = 15.minutes)