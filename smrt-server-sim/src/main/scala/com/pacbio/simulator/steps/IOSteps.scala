package com.pacbio.simulator.steps

import java.nio.file.Paths

import com.pacbio.simulator.{Scenario, StepResult, RunDesignTemplateReader}

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

  case class ReadFileFromTemplate(pathVar: Var[String]) extends VarStep[String]{

    override val name = "Read File From Template"

    override def run: Future[Result] = Future {
      var xml = new RunDesignTemplateReader(Paths.get(pathVar.get)).readStr
      println(s"xml :  $xml")
      output(xml.mkString)
      SUCCEEDED
    }
  }
}