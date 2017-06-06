package com.pacbio.simulator.steps

import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult.{Result, SUCCEEDED}

import scala.concurrent.Future

trait VarSteps {
  this: Scenario =>

  // Trait for steps that produce a value which can be assigned to a ScenarioVar
  trait VarStep[T] extends Step {
    private[VarSteps] var outputVar: Option[Var[T]] = None

    /**
      * Return a computed value (often from the webservice calls)
      *
      * The name is sub-optimal. Should be improved.
      *
      * @return
      */
    def runWith: Future[T]

    /**
      * Convenience method for returning successful Results
      *
      * For other cases, users can override both {{{run}}} and {{{runWith}}}
      *
      * @return
      */
    override def run: Future[Result] = runWith.map { r =>
      output(r)
      SUCCEEDED
    }
    protected final def output(value: T): Unit = outputVar.foreach(_.set(value))
  }

  object Var {
    def apply[T](init: T) = new Var[T](Some(init))
    def apply[T]() = new Var[T]()
  }

  sealed class Var[T](init: Option[T] = None) {
    private[VarSteps] var value: Option[T] = init
    private[VarSteps] def set(v: T) = value = Some(v)

    def := (step: VarStep[T]): Step = {
      step.outputVar = Some(this)
      step
    }

    def get: T = value.get

    def isDefined: Boolean = value.isDefined

    def mapWith[U](map: T => U): Var[U] = new MappedVar[T, U](this, map)

    def ? (cond: T => Boolean): Var[Boolean] = mapWith[Boolean](cond)

    def ==? (value: T): Var[Boolean] = ? (_ == value)
    def !=? (value: T): Var[Boolean] = ? (_ != value)

    def ==? (other: Var[T]): Var[Boolean] = mapWith { v => v == other.get}
    def !=? (other: Var[T]): Var[Boolean] = mapWith { v => v != other.get}
  }

  private[VarSteps] class MappedVar[F, T](from: Var[F], map: F => T) extends Var[T](None) {
    override def := (step: VarStep[T]): Step =
      throw new UnsupportedOperationException("Illegal assignment to MappedVar")

    override def get: T = map(from.get)
  }
}