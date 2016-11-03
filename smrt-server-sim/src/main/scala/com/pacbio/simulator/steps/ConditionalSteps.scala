package com.pacbio.simulator.steps

import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult._

import scala.concurrent.Future
import scala.util.control.NonFatal

trait ConditionalSteps {
  this: Scenario with VarSteps =>

  implicit class ConditionalStep(s: Step) {
    def IF (cond: Var[Boolean]): Step = IfStep(cond, s)
    def SHOULD_FAIL: Step = ExpectFailStep(s)
    def SHOULD_RAISE: Step = ExpectErrorStep(s)
  }

  case class IfStep[T](v: Var[Boolean], step: Step) extends Step {
    override val name = s"Conditional-${step.name}"
    override def run: Future[Result] = if (v.get) step.run else Future { SUPPRESSED }
  }

  case class ExpectFailStep(step: Step) extends Step {
    override val name = s"ExpectFail-${step.name}"
    override def run: Future[Result] = step.run.map {
      case FAILED(_, _) => SUCCEEDED
      case r => FAILED(s"Expected FAILED result but was $r")
    }
  }

  case class ExpectErrorStep(step: Step) extends Step {
    override val name = s"ExpectError-${step.name}"
    override def run: Future[Result] = step.run.recoverWith {
      case NonFatal(ex) => Future { EXCEPTION(ex) }
    }.map {
      case EXCEPTION(_, _) => SUCCEEDED
      case r => FAILED(s"Expected EXCEPTION result but was $r")
    }
  }

  def fail(failMsg: String): Step = FailStep(failMsg)

  case class FailStep[T](failMsg: String) extends Step {
    override val name = "Fail"
    override def run: Future[Result] = Future { FAILED(failMsg) }
  }

  def exception(ex: Exception): Step = ExceptionStep(ex)

  case class ExceptionStep(ex: Exception) extends Step {
    override val name = "Exception"
    override def run: Future[Result] = Future { throw ex }
  }
}