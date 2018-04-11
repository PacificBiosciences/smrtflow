package com.pacbio.secondary.smrtlink.jobtypes

import java.util.UUID

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels._
import CommonModelImplicits._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{DaoFutureUtils, JobsDao}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models._
import spray.json._
import DefaultJsonProtocol._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  *
  * @param jobs        Deferred jobs that should be run
  * @param name        Name of the MultiJob
  * @param description Description of the MultiJob
  * @param projectId   Project id to assign the MultiJobs. If provided the children jobs
  *                    do not have an explicit project Id assigned, the mulit-job
  *                    project id will be used.
  * @param submit      To submit the job after creation. This will make the job unedtiable.
  */
case class MultiAnalysisJobOptions(jobs: Seq[DeferredJob],
                                   name: Option[String],
                                   description: Option[String],
                                   projectId: Option[Int] = Some(
                                     JobConstants.GENERAL_PROJECT_ID),
                                   submit: Option[Boolean] = Some(false))
    extends ServiceMultiJobOptions {

  override def getSubmit(): Boolean =
    submit.getOrElse(JobConstants.SUBMIT_DEFAULT_MULTI_JOB)

  override def jobTypeId = JobTypeIds.MJOB_MULTI_ANALYSIS

  override def validate(dao: JobsDao, config: SystemJobConfig) = None

  override def toJob() = new MultiAnalysisJob(this)

  override def toMultiJob() = new MultiAnalysisJob(this)

  /**
    * Warning, this is a bit different that the "Core" Job and the Entry Points
    * might not be imported into the system yet (or ever).
    *
    * @param dao JobsDoa
    * @return
    */
  override def resolveEntryPoints(
      dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    jobs.flatMap(
      _.entryPoints
        .map(ep => EngineJobEntryPointRecord(ep.uuid, ep.fileTypeId)))
  }
}

case class MultiAnalysisWorkflow(jobIds: Seq[Option[Int]])

class MultiAnalysisJob(opts: MultiAnalysisJobOptions)
    extends ServiceCoreJob(opts)
    with ServiceMultiJobModel
    with DaoFutureUtils
    with LazyLogging {
  type Out = Int

  import CommonModelImplicits._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  private def resolveEntryPoints(dao: JobsDao,
                                 entryPoints: Seq[DeferredEntryPoint])
    : Future[Seq[(DeferredEntryPoint, DataSetMetaDataSet)]] = {
    Future.sequence(
      entryPoints.map(ep => dao.getDataSetById(ep.uuid).map(d => (ep, d))))
  }
}
