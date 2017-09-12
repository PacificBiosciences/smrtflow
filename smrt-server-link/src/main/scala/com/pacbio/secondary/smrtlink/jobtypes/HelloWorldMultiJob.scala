package com.pacbio.secondary.smrtlink.jobtypes

import java.util.UUID

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobResultWriter
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig


case class HelloWorldMultiJobOptions(subreadSet: UUID,
                                     name: Option[String],
                                     description: Option[String],
                                     projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {

  override def jobTypeId = JobTypeIds.MJOB_HELLO_WORLD
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new HelloWorldMultiJob(this)

}


class HelloWorldMultiJob(opts: HelloWorldMultiJobOptions) extends ServiceCoreJob(opts) {
  type Out = Int

  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig) = {
    Right(1)
  }
}