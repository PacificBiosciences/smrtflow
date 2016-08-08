package com.pacbio.simulator.steps

import java.util.UUID

import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.models._
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

  case class GetDataSet(dsId: Var[UUID]) extends VarStep[DataSetMetaDataSet] {
    override val name = "GetDataSet"

    override def run: Future[Result] = smrtLinkClient.getDataSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetSubreadSets extends VarStep[Seq[SubreadServiceDataSet]] {
    override val name = "GetSubreadSets"
    override def run: Future[Result] = smrtLinkClient.getSubreadSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetSubreadSet(dsId: Var[UUID]) extends VarStep[SubreadServiceDataSet] {
    override val name = "GetSubreadSet"
    override def run: Future[Result] = smrtLinkClient.getSubreadSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetHdfSubreadSets extends VarStep[Seq[HdfSubreadServiceDataSet]] {
    override val name = "GetHdfSubreadSets"
    override def run: Future[Result] = smrtLinkClient.getHdfSubreadSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetHdfSubreadSet(dsId: Var[UUID]) extends VarStep[HdfSubreadServiceDataSet] {
    override val name = "GetHdfSubreadSet"
    override def run: Future[Result] = smrtLinkClient.getHdfSubreadSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetReferenceSets extends VarStep[Seq[ReferenceServiceDataSet]] {
    override val name = "GetReferenceSets"
    override def run: Future[Result] = smrtLinkClient.getReferenceSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetReferenceSet(dsId: Var[UUID]) extends VarStep[ReferenceServiceDataSet] {
    override val name = "GetReferenceSet"
    override def run: Future[Result] = smrtLinkClient.getReferenceSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetBarcodeSets extends VarStep[Seq[BarcodeServiceDataSet]] {
    override val name = "GetBarcodeSets"
    override def run: Future[Result] = smrtLinkClient.getBarcodeSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetBarcodeSet(dsId: Var[UUID]) extends VarStep[BarcodeServiceDataSet] {
    override val name = "GetBarcodeSet"
    override def run: Future[Result] = smrtLinkClient.getBarcodeSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetAlignmentSets extends VarStep[Seq[AlignmentServiceDataSet]] {
    override val name = "GetAlignmentSets"
    override def run: Future[Result] = smrtLinkClient.getAlignmentSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetAlignmentSet(dsId: Var[UUID]) extends VarStep[AlignmentServiceDataSet] {
    override val name = "GetAlignmentSet"
    override def run: Future[Result] = smrtLinkClient.getAlignmentSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetConsensusReadSets extends VarStep[Seq[ConsensusReadServiceDataSet]] {
    override val name = "GetConsensusReadSets"
    override def run: Future[Result] = smrtLinkClient.getConsensusReadSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetConsensusReadSet(dsId: Var[UUID]) extends VarStep[ConsensusReadServiceDataSet] {
    override val name = "GetConsensusReadSet"
    override def run: Future[Result] = smrtLinkClient.getConsensusReadSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetConsensusAlignmentSets extends VarStep[Seq[ConsensusAlignmentServiceDataSet]] {
    override val name = "GetConsensusAlignmentSets"
    override def run: Future[Result] = smrtLinkClient.getConsensusAlignmentSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetConsensusAlignmentSet(dsId: Var[UUID]) extends VarStep[ConsensusAlignmentServiceDataSet] {
    override val name = "GetConsensusAlignmentSet"
    override def run: Future[Result] = smrtLinkClient.getConsensusAlignmentSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetContigSets extends VarStep[Seq[ContigServiceDataSet]] {
    override val name = "GetContigSets"
    override def run: Future[Result] = smrtLinkClient.getContigSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetContigSet(dsId: Var[UUID]) extends VarStep[ContigServiceDataSet] {
    override val name = "GetContigSet"
    override def run: Future[Result] = smrtLinkClient.getContigSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case object GetGmapReferenceSets extends VarStep[Seq[GmapReferenceServiceDataSet]] {
    override val name = "GetGmapReferenceSets"
    override def run: Future[Result] = smrtLinkClient.getGmapReferenceSets.map { s =>
      output(s)
      SUCCEEDED
    }
  }

  case class GetGmapReferenceSet(dsId: Var[UUID]) extends VarStep[GmapReferenceServiceDataSet] {
    override val name = "GetGmapReferenceSet"
    override def run: Future[Result] = smrtLinkClient.getGmapReferenceSetByUuid(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }
}
