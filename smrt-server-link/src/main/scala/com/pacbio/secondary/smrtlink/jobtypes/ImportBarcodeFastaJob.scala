package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, InvalidJobOptionError, JobResultWriter}
import com.typesafe.scalalogging.LazyLogging
import spray.json._


//FIXME(mpkocher)(8-17-2017) There's a giant issue with the job "name" versus "name" used in job options.
case class ImportBarcodeFastaJobOptions(path: String, name: Option[String], description: Option[String],
                                        projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)
                                       ) extends ServiceJobOptions {
  override val jobTypeId: JobTypeId = JobTypeIds.HELLO_WORLD
  override def validate() = None
  override def toJob() = new ImportBarcodeFastaJob(this)
}

class ImportBarcodeFastaJob(opts: ImportBarcodeFastaJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}