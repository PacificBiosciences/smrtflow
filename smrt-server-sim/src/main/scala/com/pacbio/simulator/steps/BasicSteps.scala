package com.pacbio.simulator.steps

import com.pacbio.simulator._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration._

trait BasicSteps extends LazyLogging { this: Scenario =>

  import StepResult._

  case class SleepStep(duration: FiniteDuration) extends Step {

    override val name = s"Sleep-${duration.toMillis}"

    override def run: Future[Result] = Future {
      Thread.sleep(duration.toMillis)
      SUCCEEDED
    }
  }
}
