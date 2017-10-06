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

  override def jobTypeId = JobTypeIds.MJOB_MULTI_ANALYSIS

  override def validate(dao: JobsDao, config: SystemJobConfig) = None

  override def toJob() = new MultiAnalysisJob(this)

  override def toMultiJob() = new MultiAnalysisJob(this)

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

  implicit val multiAnalysisWorkflowFormat = jsonFormat1(MultiAnalysisWorkflow)

  private def resolveEntryPoints(dao: JobsDao,
                                 entryPoints: Seq[DeferredEntryPoint])
    : Future[Seq[(DeferredEntryPoint, DataSetMetaDataSet)]] = {
    Future.sequence(
      entryPoints.map(ep => dao.getDataSetById(ep.uuid).map(d => (ep, d))))
  }

  private def toPbsmrtpipeOptions(
      job: DeferredJob,
      entryPoints: Seq[BoundServiceEntryPoint]): PbsmrtpipeJobOptions = {
    PbsmrtpipeJobOptions(job.name,
                         job.description,
                         job.pipelineId,
                         entryPoints,
                         job.taskOptions,
                         job.workflowOptions,
                         job.projectId)
  }

  private def createPbsmrtpipeJob(
      dao: JobsDao,
      entryPoints: Seq[BoundServiceEntryPoint],
      job: DeferredJob,
      parentJobId: Int,
      user: Option[String],
      smrtLinkVersion: Option[String],
      writer: JobResultsWriter): Future[EngineJob] = {
    dao
      .createCoreJob(
        UUID.randomUUID(),
        job.name.getOrElse("Job-Name"),
        job.description.getOrElse("Job-Description"),
        JobTypeIds.PBSMRTPIPE,
        job.entryPoints.map(e =>
          EngineJobEntryPointRecord(e.uuid, e.fileTypeId)),
        toPbsmrtpipeOptions(job, entryPoints).toJson.asJsObject,
        createdBy = None,
        createdByEmail = None,
        smrtLinkVersion = smrtLinkVersion,
        parentMultiJobId = Some(parentJobId),
        projectId = job.projectId.getOrElse(JobConstants.GENERAL_PROJECT_ID)
      )
      .map { job =>
        writer.writeLine(
          s"MultiJob id:$parentJobId Created Core ${job.jobTypeId} job id:${job.id}")
        job
      }
  }

  /**
    * Resolve the Entry Points and get or create the job if it doesn't exist.
    *
    *
    */
  private def runJob(dao: JobsDao,
                     job: DeferredJob,
                     jobId: Option[Int],
                     parentJobId: Int,
                     user: Option[String],
                     smrtLinkVersion: Option[String],
                     writer: JobResultsWriter): Future[EngineJob] = {
    for {
      resolvedEntryPoints <- resolveEntryPoints(dao, job.entryPoints)
      engineJobEntryPoints <- Future.successful(resolvedEntryPoints.map(f =>
        BoundServiceEntryPoint(f._1.entryId, f._1.fileTypeId, f._2.uuid)))
      engineJob <- jobId
        .map(i => dao.getJobById(i))
        .getOrElse(
          createPbsmrtpipeJob(dao,
                              engineJobEntryPoints,
                              job,
                              parentJobId,
                              user,
                              smrtLinkVersion,
                              writer))
    } yield engineJob
  }

  // Load the workflow state. This should really be initialized correctly when the initially MultiJob is created.
  // Currently it will default to {} as the default workflow JsObject
  def loadWorkflowOrInitialize(engineJob: EngineJob): MultiAnalysisWorkflow = {
    if (JsObject.empty == engineJob.workflow.parseJson.asJsObject) {
      val ids: Seq[Option[Int]] = opts.jobs.map(_ => None)
      MultiAnalysisWorkflow(ids)
    } else {
      loadWorkflowState(engineJob.workflow)
    }
  }

  def loadWorkflowState(sx: String): MultiAnalysisWorkflow = {
    sx.parseJson.convertTo[MultiAnalysisWorkflow]
  }

  private def fetchJobState(
      dao: JobsDao,
      jobId: Option[Int]): Future[AnalysisJobStates.JobStates] = {
    jobId
      .map { i =>
        dao.getJobById(i).map(_.state)
      }
      .getOrElse(Future.successful(AnalysisJobStates.FAILED))
  }

  private def fetchJobStates(
      dao: JobsDao,
      jobIds: Seq[Option[Int]]): Future[Seq[AnalysisJobStates.JobStates]] = {
    val fetcher = fetchJobState(dao, _: Option[Int])
    runFuturesSequentially(jobIds)(fetcher)
  }

  /**
    * Collapses a list of core job states into a single state that captures the state
    * of the multi-job.
    *
    * Any core job in Submitted, or Running state will translate into RUNNING at
    * the mulit-job level
    *
    * @return
    */
  private def reduceJobStates(
      s1: AnalysisJobStates.JobStates,
      s2: AnalysisJobStates.JobStates): AnalysisJobStates.JobStates = {
    (s1, s2) match {
      case (AnalysisJobStates.CREATED, AnalysisJobStates.CREATED) =>
        AnalysisJobStates.RUNNING
      case (AnalysisJobStates.RUNNING, AnalysisJobStates.RUNNING) =>
        AnalysisJobStates.RUNNING
      case (AnalysisJobStates.SUCCESSFUL, AnalysisJobStates.SUCCESSFUL) =>
        AnalysisJobStates.SUCCESSFUL
      case (AnalysisJobStates.SUBMITTED, AnalysisJobStates.SUBMITTED) =>
        AnalysisJobStates.RUNNING
      case (AnalysisJobStates.SUBMITTED, AnalysisJobStates.CREATED) =>
        AnalysisJobStates.RUNNING
      case (AnalysisJobStates.CREATED, AnalysisJobStates.SUBMITTED) =>
        AnalysisJobStates.RUNNING
      // This is unclear what this means. Defaulting to Failed
      case (AnalysisJobStates.UNKNOWN, AnalysisJobStates.UNKNOWN) =>
        AnalysisJobStates.FAILED
      // If any child job has failed, then fail all jobs
      case (AnalysisJobStates.FAILED, _) => AnalysisJobStates.FAILED
      case (_, AnalysisJobStates.FAILED) => AnalysisJobStates.FAILED
      // If any child job has been Terminated, then mark the multi-job as failed
      case (AnalysisJobStates.TERMINATED, _) => AnalysisJobStates.FAILED
      case (_, AnalysisJobStates.TERMINATED) => AnalysisJobStates.FAILED
      // If
      case (AnalysisJobStates.RUNNING, _) => AnalysisJobStates.RUNNING
      case (_, AnalysisJobStates.RUNNING) => AnalysisJobStates.RUNNING
      case (_, AnalysisJobStates.CREATED) => AnalysisJobStates.RUNNING
      case (AnalysisJobStates.CREATED, _) => AnalysisJobStates.RUNNING
      case (_, _) =>
        logger.error(
          s"Unclear mapping of multi-job state from $s1 and $s2. Defaulting to ${AnalysisJobStates.FAILED}")
        AnalysisJobStates.FAILED
    }
  }

  private def reduceFromSingleState(
      state: AnalysisJobStates.JobStates): AnalysisJobStates.JobStates = {
    state match {
      case AnalysisJobStates.CREATED => AnalysisJobStates.RUNNING
      case AnalysisJobStates.RUNNING => AnalysisJobStates.RUNNING
      case AnalysisJobStates.SUBMITTED => AnalysisJobStates.RUNNING
      case AnalysisJobStates.FAILED => AnalysisJobStates.FAILED
      case AnalysisJobStates.TERMINATED => AnalysisJobStates.FAILED
      case AnalysisJobStates.UNKNOWN => AnalysisJobStates.FAILED
      case AnalysisJobStates.SUCCESSFUL => AnalysisJobStates.SUCCESSFUL
    }
  }

  /**
    * Collapse the Children job states into a reflection of the overall state of the MultiJob
    */
  private def determineMultiJobState(states: Seq[AnalysisJobStates.JobStates])
    : AnalysisJobStates.JobStates = {
    states match {
      case Nil => AnalysisJobStates.FAILED
      case s1 :: Nil => reduceFromSingleState(s1)
      case _ => states.reduce(reduceJobStates)
    }
  }

  def andLog(sx: String): Future[String] = Future {
    logger.info(sx)
    sx
  }

  override def runWorkflow(
      engineJob: EngineJob,
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Future[MessageResponse] = {

    val workflow = loadWorkflowOrInitialize(engineJob)

    val noJobId: Option[Int] = None

    def runner(xs: (DeferredJob, Option[Int])): Future[Option[Int]] = {
      val (deferredJob, jobId) = xs
      runJob(dao,
             deferredJob,
             jobId,
             engineJob.id,
             engineJob.createdBy,
             config.smrtLinkVersion,
             resultsWriter)
        .map(createdJob => Some(createdJob.id))
        .recoverWith { case _ => Future.successful(noJobId) }
    }

    /**
      * If the workflow or the multi-job state has changed, then update the state in db
      */
    def updateIfNecessary(
        state: AnalysisJobStates.JobStates,
        updatedWorkflow: MultiAnalysisWorkflow): Future[MessageResponse] = {
      if ((state != engineJob.state) || (updatedWorkflow != workflow)) {
        for {
          msg <- Future.successful(
            s"Updating multi-job state from ${engineJob.state} to $state")
          _ <- dao.updateMultiJobState(engineJob.id,
                                       state,
                                       updatedWorkflow.toJson.asJsObject,
                                       msg,
                                       None)
          _ <- Future.successful(resultsWriter.writeLine(msg))
        } yield MessageResponse(msg)
      } else {
        Future.successful(MessageResponse(
          "Skipping update. No change in multi-job state or workflow state."))
      }
    }

    // Book-keeping, the the jobIds (when created) list will positionally index into the list of DeferredJobs
    // The default values will be an Seq(None, None, None, ... n) for n Deferred Jobs.
    val items: Seq[(DeferredJob, Option[Int])] = opts.jobs.zip(workflow.jobIds)

    def andJobLog(sx: String) =
      andLog(s"multi-job id:${engineJob.id} $sx")

    def summary(sx: Seq[(Option[Int], AnalysisJobStates.JobStates)]): String =
      sx.map { case (i, j) => s"${i.getOrElse("ID NOT ASSIGNED YET")}:$j" }
        .reduce(_ + " " + _)

    for {
      updatedJobIds <- runFuturesSequentially(items)(runner)
      _ <- andJobLog(s"Job Ids $updatedJobIds")
      updatedWorkflow <- Future.successful(
        MultiAnalysisWorkflow(updatedJobIds))
      jobStates <- fetchJobStates(dao, updatedJobIds)
      _ <- andJobLog(
        s"Got MultiJob Child Job Summary (id:state) ${summary(updatedJobIds.zip(jobStates))}")
      multiJobState <- Future.successful(determineMultiJobState(jobStates))
      msg <- updateIfNecessary(multiJobState, updatedWorkflow)
    } yield msg
  }
}
