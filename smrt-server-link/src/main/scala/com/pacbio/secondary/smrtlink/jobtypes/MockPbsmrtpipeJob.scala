package com.pacbio.secondary.smrtlink.jobtypes

import java.net.URI
import java.nio.file.Path

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobResultWriter
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MockPbSmrtPipeJobOptions
import com.pacbio.secondary.smrtlink.models.BoundServiceEntryPoint
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

/**
  * Created by mkocher on 8/17/17.
  */
case class MockPbsmrtpipeJobOptions(
    name: Option[String],
    description: Option[String],
    pipelineId: String,
    entryPoints: Seq[BoundServiceEntryPoint],
    taskOptions: Seq[ServiceTaskOptionBase],
    workflowOptions: Seq[ServiceTaskOptionBase],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.MOCK_PBSMRTPIPE
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new MockPbsmrtpipeJob(this)
}

class MockPbsmrtpipeJob(opts: MockPbsmrtpipeJobOptions)
    extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore
  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    //Ignore the entry points provided
    val entryPoints: Seq[BoundEntryPoint] = Seq.empty[BoundEntryPoint]

    val envPath: Option[Path] = None

    val oldOpts = MockPbSmrtPipeJobOptions(opts.pipelineId,
                                           entryPoints,
                                           opts.taskOptions,
                                           opts.workflowOptions,
                                           envPath,
                                           opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
