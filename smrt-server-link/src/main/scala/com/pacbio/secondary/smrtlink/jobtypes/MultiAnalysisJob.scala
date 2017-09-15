package com.pacbio.secondary.smrtlink.jobtypes

import java.util.UUID

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{DaoFutureUtils, JobsDao}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobResultWriter
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models._
import spray.json._
import DefaultJsonProtocol._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class MultiAnalysisJobOptions(jobs: Seq[DeferredJob],
                                   name: Option[String],
                                   description: Option[String],
                                   projectId: Option[Int] = Some(
                                     JobConstants.GENERAL_PROJECT_ID))
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
      smrtLinkVersion: Option[String]): Future[EngineJob] = {
    dao.createCoreJob(
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
                     smrtLinkVersion: Option[String]): Future[EngineJob] = {
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
                              smrtLinkVersion))
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
      .getOrElse(Future.successful(AnalysisJobStates.UNKNOWN))
  }

  private def fetchJobStates(
      dao: JobsDao,
      jobIds: Seq[Option[Int]]): Future[Seq[AnalysisJobStates.JobStates]] = {
    val fetcher = fetchJobState(dao, _: Option[Int])
    runFuturesSequentially(jobIds)(fetcher)
  }

  private def reduceJobStates(
      s1: AnalysisJobStates.JobStates,
      s2: AnalysisJobStates.JobStates): AnalysisJobStates.JobStates = {
    (s1, s2) match {
      case (AnalysisJobStates.CREATED, AnalysisJobStates.CREATED) =>
        AnalysisJobStates.CREATED
      case (AnalysisJobStates.RUNNING, AnalysisJobStates.RUNNING) =>
        AnalysisJobStates.RUNNING
      case (AnalysisJobStates.SUCCESSFUL, AnalysisJobStates.SUCCESSFUL) =>
        AnalysisJobStates.SUCCESSFUL
      case (AnalysisJobStates.SUBMITTED, AnalysisJobStates.SUBMITTED) =>
        AnalysisJobStates.SUBMITTED
      case (AnalysisJobStates.UNKNOWN, AnalysisJobStates.UNKNOWN) =>
        AnalysisJobStates.UNKNOWN // This is unclear what this means
      case (AnalysisJobStates.FAILED, _) => AnalysisJobStates.FAILED
      case (_, AnalysisJobStates.FAILED) => AnalysisJobStates.FAILED
      case (AnalysisJobStates.TERMINATED, _) =>
        AnalysisJobStates.FAILED // The core job failed, mark multi job as failed
      case (_, AnalysisJobStates.TERMINATED) => AnalysisJobStates.FAILED //
      case (AnalysisJobStates.RUNNING, _) =>
        AnalysisJobStates.RUNNING // If any job is running, mark the multi as running
      case (_, AnalysisJobStates.RUNNING) => AnalysisJobStates.RUNNING
      case (_, _) =>
        logger.info(
          s"Unclear mapping of multi-job state from $s1 and $s2. Defaulting to ${AnalysisJobStates.UNKNOWN}")
        AnalysisJobStates.UNKNOWN
    }
  }

  override def runWorkflow(
      engineJob: EngineJob,
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Future[MessageResponse] = {

    val workflow = loadWorkflowOrInitialize(engineJob)

    val noJobId: Option[Int] = None

    def runner(xs: (DeferredJob, Option[Int])): Future[Option[Int]] = {
      val (deferredJob, jobId) = xs
      runJob(dao, deferredJob, jobId, engineJob.id, config.smrtLinkVersion)
        .map(createdJob => Some(createdJob.id))
        .recoverWith { case _ => Future.successful(noJobId) }
    }

    // Book-keeping, the the jobIds (when created) list will positionally index into the list of DeferredJobs
    // The default values will be an Seq(None, None, None, ... n) for n Deferred Jobs.
    val items: Seq[(DeferredJob, Option[Int])] = opts.jobs.zip(workflow.jobIds)

    for {
      updatedJobIds <- runFuturesSequentially(items)(runner)
      updatedWorkflow <- Future.successful(
        MultiAnalysisWorkflow(updatedJobIds))
      jobStates <- fetchJobStates(dao, updatedJobIds)
      multiJobState <- Future.successful(jobStates.reduce(reduceJobStates))
      updatedMultiJob <- dao.updateMultiJobState(
        engineJob.id,
        multiJobState,
        updatedWorkflow.toJson.asJsObject,
        s"Updating state to $multiJobState",
        None)
    } yield
      MessageResponse(
        s"Successfully checked MultiJob ${updatedMultiJob.id} ${jobTypeId.id} in state:${updatedMultiJob.state}")

  }
}
