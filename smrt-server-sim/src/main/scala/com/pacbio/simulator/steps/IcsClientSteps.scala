package com.pacbio.simulator.steps


import java.util.UUID

import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports.ReportModels
import com.pacbio.common.tools.GetSmrtServerStatus
import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult._
import com.pacbio.common.models._
import com.pacbio.simulator.clients.InstrumentControlClient
import com.pacificbiosciences.pacbiodatasets._
import java.net.URL
import scala.concurrent.Future

import com.pacbio.simulator.ICSModel.{ICSRun, RunObj}
import com.pacbio.simulator.ICSJsonProtocol._
import spray.json.JsValue

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
/**
  * Created by amaster on 2/10/17.
  */
trait IcsClientSteps {
  this: Scenario with VarSteps =>

  case class SleepStep(duration: FiniteDuration) extends Step {

    override val name = s"Sleep-${duration.toMillis}"

    override def run: Future[Result] = Future {
      Thread.sleep(duration.toMillis)
      SUCCEEDED
    }

  }

  case class PostRunDesignToICS(icsHost:String, icsPort : Int, runDesign : Var[Run])  extends Step {
    override val name = "Post RunDesign to ICS"

    def generateIcsRun  :Future[ICSRun]= {
      val runObj = RunObj(runDesign.get.dataModel, runDesign.get.uniqueId, runDesign.get.summary)
     //ICSRun(runDesign.get.createdBy.get, runObj)
      runDesign.get.createdBy match {
        case Some(ss) =>  Future(ICSRun(ss, runObj))
        case None =>  Future.failed(throw new Exception("runDesign missing createdBy, this field is needed by ICS"))
      }
    }

    def postRun(run: ICSRun, icsClient : InstrumentControlClient) ={
      icsClient.postRun(run)
    }

    override def run: Future[Result] = {
      val icsClient = new InstrumentControlClient(new URL("http",icsHost, icsPort,""))

      var response = for{
        run <-  generateIcsRun
        status <- postRun(run, icsClient)
      } yield status

      response.map{ res =>
        println(s"POST /run response from ICS : $res")
        SUCCEEDED
      }

    }//smrtLinkClient.deleteRun(runId.get).map(_ => SUCCEEDED)
  }

  case class PostStartRunToICS(icsHost:String, icsPort : Int) extends Step{
    override val name = "Post start run to ICS"

    override def run : Future[Result] = {
      val icsClient = new InstrumentControlClient(new URL("http",icsHost, icsPort,""))
      val response = for {
        res <- icsClient.postRunStart
      } yield res

      response.map{rr =>
        println(s"POST /run/start response from ICS : $rr")
        SUCCEEDED
      }
    }
  }

}
