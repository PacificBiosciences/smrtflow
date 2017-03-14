package com.pacbio.simulator.steps

import java.util.UUID

import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports.ReportModels
import com.pacbio.common.tools.GetSmrtServerStatus
import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult._
import com.pacbio.common.models._
import com.pacificbiosciences.pacbiodatasets._

import scala.concurrent.Future

trait SmrtLinkSteps {
  this: Scenario with VarSteps =>

  import CommonModelImplicits._
  import ReportModels._

  val smrtLinkClient: SmrtLinkServiceAccessLayer

  case object GetStatus extends VarStep[Int] with GetSmrtServerStatus {
    override val name = "GetStatus"
    override def run: Future[Result] = Future {
      getStatus(smrtLinkClient, 5, 8)
    }.map { x => 
      output(x)
      SUCCEEDED
    }
  }

  case object GetRuns extends VarStep[Seq[RunSummary]] {
    override val name = "GetRuns"

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

  case class GetCollections(runId: Var[UUID]) extends VarStep[Seq[CollectionMetadata]] {
    override val name = "GetCollections"

    override def run: Future[Result] = smrtLinkClient.getCollections(runId.get).map { r =>
      output(r)
      SUCCEEDED
    }
  }

  case class GetCollection(runId: Var[UUID], collectionId: Var[UUID]) extends VarStep[CollectionMetadata] {
    override val name = "GetCollection"

    override def run: Future[Result] = smrtLinkClient.getCollection(runId.get, collectionId.get).map { r =>
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

    override def run: Future[Result] = smrtLinkClient.getDataSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class DeleteDataSet(dsId: Var[UUID]) extends VarStep[String] {
    override val name = "DeleteDataSet"
    override def run: Future[Result] = smrtLinkClient.deleteDataSet(dsId.get).map { m =>
      output(m.message)
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
    override def run: Future[Result] = smrtLinkClient.getSubreadSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetSubreadSetDetails(dsId: Var[UUID]) extends VarStep[SubreadSet] {
    override val name = "GetSubreadSetDetails"
    override def run: Future[Result] = smrtLinkClient.getSubreadSetDetails(dsId.get).map { d =>
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
    override def run: Future[Result] = smrtLinkClient.getHdfSubreadSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetHdfSubreadSetDetails(dsId: Var[UUID]) extends VarStep[HdfSubreadSet] {
    override val name = "GetHdfSubreadSetDetails"
    override def run: Future[Result] = smrtLinkClient.getHdfSubreadSetDetails(dsId.get).map { d =>
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
    override def run: Future[Result] = smrtLinkClient.getReferenceSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetReferenceSetDetails(dsId: Var[UUID]) extends VarStep[ReferenceSet] {
    override val name = "GetReferenceSetDetails"
    override def run: Future[Result] = smrtLinkClient.getReferenceSetDetails(dsId.get).map { d =>
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
    override def run: Future[Result] = smrtLinkClient.getBarcodeSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetBarcodeSetDetails(dsId: Var[UUID]) extends VarStep[BarcodeSet] {
    override val name = "GetBarcodeSetDetails"
    override def run: Future[Result] = smrtLinkClient.getBarcodeSetDetails(dsId.get).map { d =>
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
    override def run: Future[Result] = smrtLinkClient.getAlignmentSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetAlignmentSetDetails(dsId: Var[UUID]) extends VarStep[AlignmentSet] {
    override val name = "GetAlignmentSetDetails"
    override def run: Future[Result] = smrtLinkClient.getAlignmentSetDetails(dsId.get).map { d =>
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
    override def run: Future[Result] = smrtLinkClient.getConsensusReadSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetConsensusReadSetDetails(dsId: Var[UUID]) extends VarStep[ConsensusReadSet] {
    override val name = "GetConsensusReadSetDetails"
    override def run: Future[Result] = smrtLinkClient.getConsensusReadSetDetails(dsId.get).map { d =>
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
    override def run: Future[Result] = smrtLinkClient.getConsensusAlignmentSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetConsensusAlignmentSetDetails(dsId: Var[UUID]) extends VarStep[ConsensusAlignmentSet] {
    override val name = "GetConsensusAlignmentSetDetails"
    override def run: Future[Result] = smrtLinkClient.getConsensusAlignmentSetDetails(dsId.get).map { d =>
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
    override def run: Future[Result] = smrtLinkClient.getContigSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetContigSetDetails(dsId: Var[UUID]) extends VarStep[ContigSet] {
    override val name = "GetContigSetDetails"
    override def run: Future[Result] = smrtLinkClient.getContigSetDetails(dsId.get).map { d =>
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
    override def run: Future[Result] = smrtLinkClient.getGmapReferenceSet(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetGmapReferenceSetDetails(dsId: Var[UUID]) extends VarStep[GmapReferenceSet] {
    override val name = "GetGmapReferenceSetDetails"
    override def run: Future[Result] = smrtLinkClient.getGmapReferenceSetDetails(dsId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetSubreadSetReports(dsId: Var[UUID]) extends VarStep[Seq[DataStoreReportFile]] {
    override val name = "GetSubreadSetReports"
    override def run: Future[Result] = smrtLinkClient.getSubreadSetReports(dsId.get).map { r =>
      output(r)
      SUCCEEDED
    }
  }

  case class GetReport(reportId: Var[UUID]) extends VarStep[Report] {
    override val name = "GetReport"
    override def run: Future[Result] = smrtLinkClient.getReport(reportId.get).map { r =>
      output(r)
      SUCCEEDED
    }
  }

  case class GetImportJobDataStore(jobId: Var[UUID]) extends VarStep[Seq[DataStoreServiceFile]] {
    override val name = "GetImportJobDataStore"
    override def run: Future[Result] = smrtLinkClient.getImportDatasetJobDataStore(jobId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetMergeJobDataStore(jobId: Var[UUID]) extends VarStep[Seq[DataStoreServiceFile]] {
    override val name = "GetMergeJobDataStore"
    override def run: Future[Result] = smrtLinkClient.getMergeDatasetJobDataStore(jobId.get).map { d =>
      output(d)
      SUCCEEDED
    }
  }

  case class GetDataStoreFileResource(fileId: Var[UUID], relpath: Var[String]) extends VarStep[Int] {
    override val name = "GetDataStoreFileResource"
    override def run: Future[Result] = smrtLinkClient.getDataStoreFileResource(fileId.get, relpath.get).map { a =>
      output(a.entity.data.toByteArray.size) // 'a' is Array[Byte], all we test for is the size
      SUCCEEDED
    }
  }

  case object GetProjects extends VarStep[Seq[Project]] {
    override val name = "GetProjects"
    override def run: Future[Result] = smrtLinkClient.getProjects.map { p =>
      output(p)
      SUCCEEDED
    }
  }

  case class GetProject(projectId: Var[Int]) extends VarStep[FullProject] {
    override val name = "GetProject"
    override def run: Future[Result] = smrtLinkClient.getProject(projectId.get).map { p =>
      output(p)
      SUCCEEDED
    }
  }

  case class CreateProject(projectName: Var[String], description: Var[String]) extends VarStep[Int] {
    override val name = "CreateProject"
    override def run: Future[Result] = smrtLinkClient.createProject(projectName.get, description.get).map { p =>
      output(p.id)
      SUCCEEDED
    }
  }

  case class UpdateProject(projectId: Var[Int], request: Var[ProjectRequest]) extends VarStep[FullProject] {
    override val name = "UpdateProject"
    override def run: Future[Result] = smrtLinkClient.updateProject(projectId.get, request.get).map { p =>
      output(p)
      SUCCEEDED
    }
  }
}
