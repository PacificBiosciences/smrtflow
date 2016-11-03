package com.pacbio.simulator.scenarios

import akka.actor.ActorSystem
import com.pacbio.simulator._
import com.pacbio.simulator.steps._
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps

object ExampleScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = ExampleScenario
}

object ExampleScenario extends Scenario with BasicSteps with VarSteps with ConditionalSteps {
  import StepResult._

  override val name = "ExampleScenario"

  case class SquareNumberStep(in: Var[Int]) extends VarStep[Int] {
    override val name = "SquareNumberStep"
    override def run: Future[Result] = Future {
      val out = in.get * in.get
      println(s"SquareNumberStep: Input = ${in.get}, Output = $out")
      output(out)
      SUCCEEDED
    }
  }

  val num = Var[Int](3)

  override val steps = Seq(
    // Set num to 3*3 = 9
    num := SquareNumberStep(num),

    // Sleep for 1 second
    SleepStep(1.second),

    // If num >= 5 and num < 10, then set num to num * num (9 * 9 = 81)
    (num := SquareNumberStep(num)) IF (num ? (_ < 10)),

    // If num != 81, fail
    fail("Expected 3 ^2 ^2 == 81") IF (num !=? 81),

    fail("This step should fail") SHOULD_FAIL,

    exception(new IllegalStateException("This exception should be thrown")) SHOULD_RAISE
  )
}