package com.pacbio.simulator.steps

import java.nio.file.{Path, Paths}

import com.pacbio.simulator.util.XmlAttributeManipulator
import com.pacbio.simulator.{RunDesignTemplateInfo, RunDesignTemplateReader, Scenario, StepResult}

import scala.concurrent.Future
import resource._

import scala.io.Source
import scala.xml.XML
import XML._

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

  case class ReadFile(pathVar: Var[String]) extends VarStep[RunDesignTemplateInfo]{

    override val name = "Read File From Template2"

    override def run: Future[Result] = Future {
      var runDesignInfo = new RunDesignTemplateReader(Paths.get(pathVar.get)).readRundesignTemplateInfo
      println(s"xml :  ${runDesignInfo.xml}")
      println(s"subreadSet :  ${runDesignInfo.subreadsetUuid.toString}")
      output(runDesignInfo)
      SUCCEEDED
    }
  }

  case class ReadXml(runDesignTemplateInfo : Var[RunDesignTemplateInfo]) extends VarStep[String] {

    override val name = "Read xml from RunDesignTemplateInfo"

    override def run : Future[Result] = Future{
      val rr = runDesignTemplateInfo.get
      output(rr.xml)
      SUCCEEDED
    }
  }

  case class UpdateSubreadsetXml(subreads : Var[Path], runInfo : Var[RunDesignTemplateInfo]) extends Step
    with XmlAttributeManipulator{

    override val name = "UpdateSubreadsetXml"

    def readFile = XML.loadFile(subreads.get.toString)

    def writeToFile(contents : scala.xml.Elem) = XML.save(subreads.get.toString, contents)

    def updateXml = {
      val elems = readFile
      val updatedXml = updateSubreadSetUuid(runInfo.get.subreadsetUuid,elems)
      writeToFile(updatedXml)
    }

    override def run : Future[Result] = Future{
      updateXml
      SUCCEEDED
    }
  }
}