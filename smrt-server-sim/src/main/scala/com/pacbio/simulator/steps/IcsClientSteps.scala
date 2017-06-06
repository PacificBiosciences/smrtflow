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
import com.pacbio.simulator.ICSModel.{ICSRun, RunObj}
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

  case class PostRunDesignToICS(runDesign: Var[Run]) extends Step {
    override val name = "Post RunDesign to ICS"

    def generateIcsRun: Future[ICSRun] = {
      val runObj = RunObj(runDesign.get.dataModel, runDesign.get.uniqueId, runDesign.get.summary.get)

      //ICSRun(runDesign.get.createdBy.get, runObj)
      runDesign.get.createdBy match {
        case Some(ss) => Future(ICSRun(ss, runObj))
        case None => Future.failed(throw new Exception("runDesign missing createdBy, this field is needed by ICS"))
      }
    }

    def postRun(run: ICSRun) = {
      icsClient.postRun(run)
    }

    override def run: Future[Result] = {
      //val icsClient = new InstrumentControlClient(new URL("http",icsHost, icsPort,""))

      var response = for {
        run <- generateIcsRun
        status <- postRun(run)
      } yield status

      response.map { res =>
        println(s"POST /run response from ICS : $res")
        SUCCEEDED
      }

    } //smrtLinkClient.deleteRun(runId.get).map(_ => SUCCEEDED)
  }

  case object PostRunStartToICS extends Step {
    override val name = "Post start run to ICS"

    override def run: Future[Result] = {
      val response = for {
        res <- icsClient.postRunStart
      } yield res

      response.map { rr =>
        println(s"POST /run/start response from ICS : $rr")
        SUCCEEDED
      }
    }
  }

  case object PostRunRqmtsToICS extends Step {
    override val name = "Post run rqmts to ICS"

    override def run: Future[Result] = {
      val response = for {
        res <- icsClient.postRunRqmts
      } yield res

      response.map { rr =>
        println(s"POST /run/rqmts response from ICS : $rr")
        SUCCEEDED
      }
    }
  }

  case object PostLoadInventory extends Step {
    override val name = "Post load inventory to ICS"

    override def run: Future[Result] = {
      val response = for {
        res <- icsClient.postLoadInventory
      } yield res

      response.map { rr =>
        println(s"POST /test/endtoend/inventory response from ICS : $rr")
        SUCCEEDED
      }
    }
  }

  // todo : retries must be calculated according to the movieLength
  case class GetRunStatus(runDesign: Var[Run],
                          desiredStates: Seq[ICSState.State],
                          sleepTime: FiniteDuration = 1.second,
                          retries: Int = 3000) extends Step {


    override val name = "GET Run Status"

    override def run: Future[Result] = {

      val uid = runDesign.get.uniqueId.toString
      println(s"uid : $uid")

      val failureStates: Seq[ICSState.State] = Seq(Aborted, Aborting, Terminated, Unknown, Paused)

      val desiredStateNames: String = "[" + desiredStates.map(_.name).reduce(_ + "," + _) + "]"

      /*
      based on the api it appears that ICS will just convey the information of the current run, as opposed to all runs
      in other words, there is no need for filtering out runs based on unique_id
       */
      def stateFuture: Future[ICSState.State] = icsClient.getInstrumentState.map { rr =>
        val state = rr.runState
        ICSState.fromIndex(state).getOrElse(throw new IllegalStateException(s"Unknown state $state encountered."))
      }

      var retry = 0

      def stateFutureRep: Future[ICSState.State] = stateFuture.flatMap {
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

  // todo : retries must be calculated according to the movieLength
  case class GetRunRqmts(retries: Int = 3000,
                         sleepTime: FiniteDuration = 1.second) extends Step {


    override val name = "GET Run Rqmts"

    override def run : Future[Result] = {

      val desiredState = true
      val failureState = false

      def stateFuture = icsClient.getRunRqmts.map { rr =>
        rr.hasSufficientInventory//.getOrElse(throw new IllegalStateException(s"hasSufficientInventory field could not be found"))
      }

      var retry  = 0

      def stateFutureRep : Future[Boolean] = stateFuture.flatMap{
        state =>
          if (desiredState==state)
            Future(state)
          else if (failureState == state) {
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
        if (desiredState==state)
          SUCCEEDED
        else if (failureState == state)
          FAILED(s"GET Run Rqmts  : hasSufficientResources : FALSE")
        else
          FAILED(
            s"GET Run Rqmts  : hasSufficientResources timed out" +
              s" but after $retries retries with a sleep time of $sleepTime")
      }
    }
  }
}