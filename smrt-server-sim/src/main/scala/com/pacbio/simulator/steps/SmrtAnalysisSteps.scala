
package com.pacbio.simulator.steps

import java.util.UUID
import java.nio.file.Path

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondary.smrtserver.tools.PbService
import com.pacbio.secondary.smrtserver.models._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.simulator.Scenario
import com.pacbio.simulator.StepResult._
import com.pacbio.common.models._


trait SmrtAnalysisSteps {
  this: Scenario with VarSteps =>

  import CommonModelImplicits._

  val smrtLinkClient: AnalysisServiceAccessLayer

  case class ImportDataSet(path: Var[Path], dsType: Var[String]) extends VarStep[UUID] {
    override val name = "ImportDataSet"
    override def run: Future[Result] = smrtLinkClient.importDataSet(path.get, dsType.get).map { j =>
      output(j.uuid)
      SUCCEEDED
    }
  }

  case class WaitForJob(jobId: Var[UUID], maxTime: Var[Int] = Var(1800)) extends VarStep[Int] {
    override val name = "WaitForJob"
    override def run: Future[Result] = Future {
      // Return non-zero exit code. This probably needs to be refactored at the Sim level
      output(smrtLinkClient.pollForJob(jobId.get, maxTime.get).map(_ => 0).getOrElse(1))
      SUCCEEDED
    }
  }

  case class ImportFasta(path: Var[Path], dsName: Var[String]) extends VarStep[UUID] {
    override val name = "ImportFasta"
    override def run: Future[Result] = smrtLinkClient.importFasta(path.get, dsName.get, "lambda", "haploid").map { j =>
      output(j.uuid)
      SUCCEEDED
    }
  }

  case class ImportFastaBarcodes(path: Var[Path], dsName: Var[String]) extends VarStep[UUID] {
    override val name = "ImportFastaBarcodes"
    override def run: Future[Result] = smrtLinkClient.importFastaBarcodes(path.get, dsName.get).map { j =>
      output(j.uuid)
      SUCCEEDED
    }
  }

  case class MergeDataSets(dsType: Var[String], ids: Var[Seq[Int]], dsName: Var[String]) extends VarStep[UUID] {
    override val name = "MergeDataSets"
    override def run: Future[Result] = smrtLinkClient.mergeDataSets(dsType.get, ids.get, dsName.get).map { j =>
      output(j.uuid)
      SUCCEEDED
    }
  }

  case class ConvertRsMovie(path: Var[Path]) extends VarStep[UUID] {
    override val name = "ConvertRsMovie"
    override def run: Future[Result] = smrtLinkClient.convertRsMovie(path.get,
        "sim-convert-rs-movie").map { j =>
      output(j.uuid)
      SUCCEEDED
    }
  }

  case class ExportDataSets(dsType: Var[String], ids: Var[Seq[Int]], outputPath: Var[Path]) extends VarStep[UUID] {
    override val name = "ExportDataSets"
    override def run: Future[Result] = smrtLinkClient.exportDataSets(dsType.get, ids.get, outputPath.get).map { j =>
      output(j.uuid)
      SUCCEEDED
    }
  }

  case class RunAnalysisPipeline(pipelineOptions: Var[PbSmrtPipeServiceOptions]) extends VarStep[UUID] {
    override val name = "RunAnalysisPipeline"
    override def run: Future[Result] = smrtLinkClient.runAnalysisPipeline(pipelineOptions.get).map { j =>
      output(j.uuid)
      SUCCEEDED
    }
  }

  case class GetJob(jobId: Var[UUID]) extends VarStep[EngineJob] {
    override val name = "GetJob"
    override def run: Future[Result] = smrtLinkClient.getJob(jobId.get).map { j =>
      output(j)
      SUCCEEDED
    }
  }

  case class GetPipelineTemplateViewRule(pipelineId: Var[String]) extends VarStep[PipelineTemplateViewRule] {
    override val name = "GetPipelineTemplateViewRule"
    override def run: Future[Result] = smrtLinkClient.getPipelineTemplateViewRule(pipelineId.get).map { r =>
      output(r)
      SUCCEEDED
    }
  }

  case class GetDataStoreViewRules(pipelineId: Var[String]) extends VarStep[PipelineDataStoreViewRules] {
    override val name = "GetDataStoreViewRules"
    override def run: Future[Result] = smrtLinkClient.getPipelineDataStoreViewRules(pipelineId.get).map { r =>
      output(r)
      SUCCEEDED
    }
  }
}
