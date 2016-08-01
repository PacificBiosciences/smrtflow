package com.pacbio.simulator.steps

import com.pacbio.simulator.Scenario

trait VarSteps {
  this: Scenario =>

  // Trait for steps that produce a value which can be assigned to a ScenarioVar
  trait VarStep[T] extends Step {
    private[VarSteps] var outputVar: Option[Var[T]] = None

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
  }

  private[VarSteps] class MappedVar[F, T](from: Var[F], map: F => T) extends Var[T](None) {
    override def := (step: VarStep[T]): Step =
      throw new UnsupportedOperationException("Illegal assignment to MappedVar")

    override def get: T = map(from.get)
  }
}