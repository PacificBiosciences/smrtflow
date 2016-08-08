
package com.pacbio.simulator.steps

import java.util.UUID
import java.nio.file.Path

import scala.concurrent.Future

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
      output(smrtLinkClient.pollForJob(jobId.get))
      SUCCEEDED
    }
  }

}
