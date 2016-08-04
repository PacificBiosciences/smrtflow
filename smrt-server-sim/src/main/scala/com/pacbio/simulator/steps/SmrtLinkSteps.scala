package com.pacbio.simulator.steps

import java.util.UUID

import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.models.{RunSummary, Run}
import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult._

import scala.concurrent.Future

trait SmrtLinkSteps {
  this: Scenario with VarSteps =>

  val smrtLinkClient: SmrtLinkServiceAccessLayer

  case object GetRuns extends VarStep[Seq[RunSummary]] {
    override val name = "GetRun"

    override def run: Future[Result] = smrtLinkClient.getRuns.map { r =>
      output(r)
      SUCCEEDED
    }
  }

  case class GetRun(runId: Var[UUID]) extends VarStep[Run] {
    override val name = "GetRun"

    override def run: Future[Result] = smrtLinkClient.getRun(runId.get).map { r =>
      output(r)
      SUCCEEDED
    }
  }

  case class CreateRun(dataModel: Var[String]) extends VarStep[UUID] {
    override val name = "CreateRun"

    override def run: Future[Result] = smrtLinkClient.createRun(dataModel.get).map { r =>
      output(r.uniqueId)
      SUCCEEDED
    }
  }

  case class UpdateRun(runId: Var[UUID],
                       dataModel: Option[Var[String]] = None,
                       reserved: Option[Var[Boolean]] = None) extends Step {
    override val name = "GetRun"

    override def run: Future[Result] =
      smrtLinkClient.updateRun(runId.get, dataModel.map(_.get), reserved.map(_.get)).map(_ => SUCCEEDED)
  }

  case class DeleteRun(runId: Var[UUID]) extends Step {
    override val name = "GetRun"

    override def run: Future[Result] = smrtLinkClient.deleteRun(runId.get).map(_ => SUCCEEDED)
  }
}
