package com.pacbio.simulator.steps

import java.nio.file.{Path, Paths}

import com.pacbio.secondary.analysis.datasets.io.{DataSetLoader, DataSetWriter}
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

  case class CheckIfUUIDUpdated(subreads : Var[Path], runInfo : Var[RunDesignTemplateInfo]) extends Step {
    override val name = "CheckIfUUIDUpdated"

    def checkXml  : Result = {
      val uuid = runInfo.get.subreadsetUuid.toString

      val dd = DataSetLoader.loadSubreadSet(subreads.get)
      if(! dd.getUniqueId().equals(runInfo.get.subreadsetUuid.toString))
        FAILED(s"UUID of subreadset xml doesnt match set UUID : ${dd.getUniqueId()} != ${runInfo.get.subreadsetUuid.toString}")
      else
        SUCCEEDED
    }

    override def run : Future[Result] = Future{
      checkXml
    }
  }

  case class UpdateSubreadsetXml(subreads : Var[Path], runInfo : Var[RunDesignTemplateInfo]) extends Step{
    //with XmlAttributeManipulator{

    override val name = "UpdateSubreadsetXml"

    def updateXml = {
      val uuid = runInfo.get.subreadsetUuid.toString

      val dd = DataSetLoader.loadSubreadSet(subreads.get)
      dd.setUniqueId(uuid)

      println(s"setting subreadset uuid : $uuid")
      DataSetWriter.writeSubreadSet(dd, subreads.get)
    }

    override def run : Future[Result] = Future{
      updateXml
      SUCCEEDED
    }
  }
}