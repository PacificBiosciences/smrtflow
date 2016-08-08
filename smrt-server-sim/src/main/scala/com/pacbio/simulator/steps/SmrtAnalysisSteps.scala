
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


trait SmrtAnalysisSteps {
  this: Scenario with VarSteps =>

  val smrtLinkClient: AnalysisServiceAccessLayer

  case class ImportDataSet(path: Var[Path], dsType: Var[String]) extends VarStep[UUID] {
    override val name = "ImportDataSet"
    override def run: Future[Result] = smrtLinkClient.importDataSet(path.get, dsType.get).map { j =>
      output(j.uuid)
      SUCCEEDED
    }
  }

  case class WaitForJob(jobId: Var[UUID]) extends VarStep[Int] {
    override val name = "WaitForJob"
    override def run: Future[Result] = Future {
      output(Try {
        smrtLinkClient.pollForJob(jobId.get)
      } match {
        case Success(x) => 0
        case Failure(msg) => 1
      })
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

}
