package com.pacbio.secondary.smrtlink.jobtypes

import java.net.URL
import java.util.UUID
import java.nio.file.{Path, Paths}

import com.pacbio.common.models.CommonModels._
import com.pacbio.common.models.CommonModelImplicits
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{
  TsJobBundleJobOptions => OldTsJobBundleJobOptions
}
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPointRecord
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/**
  * Created by mkocher on 8/17/17.
  */
case class TsJobBundleJobOptions(jobId: IdAble,
                                 user: String,
                                 comment: String,
                                 name: Option[String],
                                 description: Option[String],
                                 projectId: Option[Int] = Some(
                                   JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {

  import CommonModelImplicits._

  override def jobTypeId = JobTypeIds.TS_JOB
  override def toJob() = new TsJobBundleJob(this)

  override def resolveEntryPoints(dao: JobsDao) =
    Seq.empty[EngineJobEntryPointRecord]

  // Only Failure jobs can be submitted to TS for troubleshooting
  def onlyAllowFailed(job: EngineJob): Future[EngineJob] = {
    if (job.hasFailed) Future.successful(job)
    else
      Future.failed(throw new UnprocessableEntityError(
        s"Can only can send failed jobs. Job ${job.id} type:${job.jobTypeId} state:${job.state}"))
  }

  def getJob(dao: JobsDao): Future[EngineJob] = {
    for {
      engineJob <- dao.getJobById(jobId)
      validJob <- onlyAllowFailed(engineJob)
    } yield validJob
  }

  def validateEveUrl(eveURL: Option[URL]): Future[URL] = {
    eveURL match {
      case Some(u) => Future.successful(u)
      case _ =>
        Future.failed(new UnprocessableEntityError(
          "External EVE URL is not configured in System. Unable to send message to TechSupport"))
    }
  }

  /**
    * Validate the job is found and is in the FAILED state.
    *
    */
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {

    val f = for {
      _ <- validateEveUrl(config.externalEveUrl)
      job <- getJob(dao)
    } yield job

    Try(Await.result(f, DEFAULT_TIMEOUT)) match {
      case Success(_) => None
      case Failure(ex) =>
        Some(InvalidJobOptionError(s"Invalid option ${ex.getMessage}"))
    }
  }

}

class TsJobBundleJob(opts: TsJobBundleJobOptions)
    extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val engineJob = Await.result(opts.getJob(dao), opts.DEFAULT_TIMEOUT)

    val name =
      opts.name.getOrElse(s"TS Bundle for Failed Job ${opts.jobId.toIdString}")
    val jobPath: Path = Paths.get(engineJob.path)

    /// Is this really the same as "host". Or is this loaded from the SL config?
    val dnsName: Option[String] = Some(config.host)

    val manifest = TsJobManifest(
      resources.jobId,
      BundleTypes.JOB,
      1,
      JodaDateTime.now(),
      config.smrtLinkSystemId,
      dnsName,
      config.smrtLinkVersion,
      opts.user,
      Some(opts.comment),
      engineJob.jobTypeId,
      engineJob.id
    )

    // Note, this can be dramatically improved now that after the
    val oldOpts = OldTsJobBundleJobOptions(jobPath, manifest)
    oldOpts.toJob.run(resources, resultsWriter)
  }
}
