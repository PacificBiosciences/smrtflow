package com.pacbio.simulator

import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

import org.joda.time.{DateTime => JodaDateTime}

trait ScenarioLoader {
  def load(config: Option[Config])(implicit system: ActorSystem): Scenario
}

trait Scenario {
  import StepResult._

  implicit val system: ActorSystem = ActorSystem("sim")
  implicit val ec: ExecutionContext = system.dispatcher

  // Subclasses may create their own steps by overriding this, or use pre-made steps
  trait Step {
    val name: String
    def run: Future[Result]
  }

  // Subclasses override these
  val name: String
  val steps: Seq[Step]
  def setUp(): Unit = {}
  def tearDown(): Unit = {}

  private def stepPrintln(i: Int, s: String) = println(s"Step #${i+1}: ${steps(i).name} - $s")
  private def scenePrintln(s: String) = println(s"Scenario: $name - $s")
  private def diffMillis(startNanos: Long, timeNanos: Long) = (timeNanos - startNanos) / 1000000

  def run(): Future[ScenarioResult] = {
    require(steps.nonEmpty)

    println()
    scenePrintln("running...\n")

    val results: Array[StepResult] = new Array[StepResult](steps.length)
    val startNanos: mutable.Map[Int, Long] = new mutable.HashMap
    val totalStartNanos: Long = System.nanoTime()

    def stepCompleted(i: Int, result: Result) = {
      val timeNanos = System.nanoTime()
      val runTimeMillis = diffMillis(startNanos(i), timeNanos)
      val resultStr = result match {
        case SUCCEEDED => "succeeded"
        case SUPPRESSED => "suppressed"
        case FAILED(_, _) => "failed"
        case EXCEPTION(_, _) => "threw exception"
        case SKIPPED => throw new IllegalStateException("Called stepCompleted on SKIPPED step")
      }
      results(i) = StepResult(steps(i).name, runTimeMillis, result)
      stepPrintln(i, s"$resultStr after $runTimeMillis millis\n${result.longMsg}")
    }

    def runStep(i: Int): Future[Result] = {
      stepPrintln(i, "running...")
      startNanos(i) = System.nanoTime()
      steps(i).run
    }

    def skipStep(i: Int): Future[Result] = {
      stepPrintln(i, "skipped\n")
      results(i) = StepResult(steps(i).name, 0, SKIPPED)
      Future { SKIPPED }
    }

    var prev: Future[Result] = Future { SUCCEEDED }
    for (i <- steps.indices) {
      prev = prev recoverWith {
        case NonFatal(ex) =>
          ex.printStackTrace()
          Future { EXCEPTION(ex) }
      } flatMap {
        case SUCCEEDED =>
          if (i > 0) stepCompleted(i - 1, SUCCEEDED)
          runStep(i)
        case SUPPRESSED =>
          if (i > 0) stepCompleted(i - 1, SUPPRESSED)
          runStep(i)
        case SKIPPED =>
          skipStep(i)
        case r: Result => // FAILED or EXCEPTION
          if (i > 0) stepCompleted(i - 1, r)
          skipStep(i)
      }
    }

    prev = prev.andThen {
      case Success(SUCCEEDED) => // Final step succeeded
        stepCompleted(steps.indices.last, SUCCEEDED)
      case Success(SUPPRESSED) => // Final step suppressed
        stepCompleted(steps.indices.last, SUPPRESSED)
      case Success(FAILED(sm, lm)) => // Final step failed
        stepCompleted(steps.indices.last, FAILED(sm, lm))
      case Failure(ex) => // Final step threw exception
        ex.printStackTrace()
        stepCompleted(steps.indices.last, EXCEPTION(ex))
    }

    prev.map { _ =>
      val timeNanos = System.nanoTime()
      val runTimeMillis = diffMillis(totalStartNanos, timeNanos)
      scenePrintln(s"completed after $runTimeMillis millis\n")
      ScenarioResult(
        name,
        runTimeMillis,
        results,
        JodaDateTime.now())
    }
  }
}