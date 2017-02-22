package com.pacbio.simulator.steps


import java.util.UUID

import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports.ReportModels
import com.pacbio.common.tools.GetSmrtServerStatus
import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult._
import com.pacbio.common.models._
import com.pacbio.simulator.clients.{ICSState, InstrumentControlClient}
import com.pacificbiosciences.pacbiodatasets._
import java.net.URL

import scala.concurrent.Future
import com.pacbio.simulator.ICSModel.{ICSRun, RunObj, RunResponse}
import com.pacbio.simulator.ICSJsonProtocol._
import spray.json.JsValue

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import com.pacbio.simulator.clients.ICSState
import ICSState._

/**
  * Created by amaster on 2/10/17.
  */
trait IcsClientSteps {
  this: Scenario with VarSteps =>

  val icsClient: InstrumentControlClient

  case class SleepStep(duration: FiniteDuration) extends Step {

    override val name = s"Sleep-${duration.toMillis}"

    override def run: Future[Result] = Future {
      Thread.sleep(duration.toMillis)
      SUCCEEDED
    }

  }

  case class PostRunDesignToICS(runDesign : Var[Run])  extends Step {
    override val name = "Post RunDesign to ICS"

    def generateIcsRun  :Future[ICSRun]= {
      val runObj = RunObj(runDesign.get.dataModel, runDesign.get.uniqueId, runDesign.get.summary)
     //ICSRun(runDesign.get.createdBy.get, runObj)
      runDesign.get.createdBy match {
        case Some(ss) =>  Future(ICSRun(ss, runObj))
        case None =>  Future.failed(throw new Exception("runDesign missing createdBy, this field is needed by ICS"))
      }
    }

    def postRun(run: ICSRun) ={
      icsClient.postRun(run)
    }

    override def run: Future[Result] = {
      //val icsClient = new InstrumentControlClient(new URL("http",icsHost, icsPort,""))

      var response = for{
        run <-  generateIcsRun
        status <- postRun(run)
      } yield status

      response.map{ res =>
        println(s"POST /run response from ICS : $res")
        SUCCEEDED
      }

    }//smrtLinkClient.deleteRun(runId.get).map(_ => SUCCEEDED)
  }

  case object PostStartRunToICS extends Step{
    override val name = "Post start run to ICS"

    override def run : Future[Result] = {
      //val icsClient = new InstrumentControlClient(new URL("http",icsHost, icsPort,""))
      val response = for {
        res <- icsClient.postRunStart
      } yield res

      response.map{rr =>
        println(s"POST /run/start response from ICS : $rr")
        SUCCEEDED
      }
    }
  }

  // todo : retries must be calculated according to the movieLength
  case class GetRunStatus(runDesign : Var[Run],
                          desiredStates : Seq[ICSState.State],
                          sleepTime: FiniteDuration = 1.second,
                          retries: Int = 300) extends VarStep[Int] {


    override val name = "GET Run Status"

    override def run : Future[Result] = {

      val uid = runDesign.get.uniqueId.toString

      val failureStates : Seq[ICSState.State] = Seq(Aborted,Aborting,Terminated,Unknown,Paused)

      val desiredStateNames: String = "[" + desiredStates.map(_.name).reduce(_ + "," + _) + "]"

      def stateFuture : Future[ICSState.State] = icsClient.getRunStatus
        .filter(_.uniqueId==uid)
        .map { rr =>
        val state = rr.status
        ICSState.fromIndex(state).getOrElse(throw new IllegalStateException(s"Unknown state $state encountered."))
      }

      var retry  = 0;

      def stateFutureRep : Future[ICSState.State] = stateFuture.flatMap{
        state =>
          if (desiredStates.contains(state))
            Future(state)
          else if (failureStates.contains(state)) {
            Future(state)
          } else if (retry < retries) {
            retry += 1
            Thread.sleep(sleepTime.toMillis)
            stateFutureRep
          } else {
            Future(state)
          }
      }

      stateFutureRep.map { state =>
        if (desiredStates.contains(state))
          SUCCEEDED
        else if (failureStates.contains(state))
          FAILED(s"Run with ${uid} failed with state : ${state.name}")
        else
          FAILED(
            s"Run ${uid} was expected to reach states $desiredStateNames," +
              s" but after $retries retries with a sleep time of $sleepTime" +
              s" in between tries, the state was ${state.name}.")
      }
    }
  }

}
