package com.pacbio.simulator.steps

import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult

import scala.concurrent.Future

import resource._

import scala.io.Source

trait IOSteps {
  this: Scenario with VarSteps =>

  import StepResult._

  case class ReadFileStep(pathVar: Var[String]) extends VarStep[String] {

    override val name = "ReadFile"

    override def run: Future[Result] = Future {
      for { s <- managed(Source.fromFile(pathVar.get)) } {
        output(s.getLines().mkString("\n"))
      }
      SUCCEEDED
    }
  }
}