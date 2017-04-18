package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.Path
import java.util.UUID

import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, BaseCoreJob, BaseJobOptions, JobResultWriter}


case class TsSystemStatusBundleOptions(smrtLinkSystemRoot: Path, manifest: TsSystemStatusManifest) extends BaseJobOptions {
  // This is just to adhere to the interface
  val projectId = 1
  override def toJob = new TsSystemStatusBundleJob(this)
}

class TsSystemStatusBundleJob(opts: TsSystemStatusBundleOptions) extends BaseCoreJob(opts: TsSystemStatusBundleOptions){

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("ts_bundle_system_status")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    Left(ResultFailed(UUID.randomUUID(), jobTypeId.id, "Message", 0, AnalysisJobStates.TERMINATED, host))
  }
}
