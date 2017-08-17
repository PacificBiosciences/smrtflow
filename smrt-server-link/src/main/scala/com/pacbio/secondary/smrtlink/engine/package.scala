package com.pacbio.secondary.smrtlink

import java.net.InetAddress
import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, InvalidJobOptionError, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


/**
  * Created by mkocher on 8/16/17.
  */
package object engine {

  trait ServiceCoreJobModel extends LazyLogging {
    type Out
    val jobTypeId: JobTypeId

    // This should be rethought
    def host = InetAddress.getLocalHost.getHostName

    def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, eventManager: ActorRef): Either[ResultFailed, Out]

  }

  trait ServiceJobOptions {
    val jobTypeId: JobTypeId
    val projectId: Int
    def toJob: ServiceCoreJob
    def validate(): Option[InvalidJobOptionError]
  }


  abstract class ServiceCoreJob(opts: ServiceJobOptions) extends ServiceCoreJobModel {
    // sugar
    val jobTypeId = opts.jobTypeId
  }


  case class HelloWorldServiceJobOptions(x: Int, override val projectId: Int = 1)(jobType: String) extends ServiceJobOptions {
    override val jobTypeId: JobTypeId = JobTypeId(jobType)
    override def validate = None
    override def toJob = new HelloWorldServiceJob(this)
  }

  class HelloWorldServiceJob(opts: HelloWorldServiceJobOptions) extends ServiceCoreJob(opts)("helloworld") {
    type Out = PacBioDataStore
    override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, eventManager: ActorRef): Either[ResultFailed, PacBioDataStore] = {
      Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
    }
  }

  class ServiceJobRunner(dao: JobsDao, eventManager: ActorRef) extends timeUtils with LazyLogging {
    import CommonModelImplicits._

    def host = InetAddress.getLocalHost.getHostName

    private def validate(opts: ServiceJobOptions, uuid: UUID, writer: JobResultWriter): Option[ResultFailed] = {
      opts.validate() match {
        case Some(errors) =>
          val msg = s"Failed to validate Job options $opts Error $errors"
          writer.writeLineStderr(msg)
          logger.error(msg)
          Some(ResultFailed(uuid, opts.jobTypeId.id, msg, 1, AnalysisJobStates.FAILED, host))
        case _ =>
          val msg = s"Successfully validated Job Options $opts"
          writer.writeLineStdout(msg)
          logger.info(msg)
          None
      }
    }

    private def updateStateToRunning(uuid: UUID): Future[EngineJob] = {
      dao.updateJobState(uuid, AnalysisJobStates.RUNNING, s"Starting to run job $uuid")
    }

    def run(opts: ServiceJobOptions, resource: JobResourceBase, writer: JobResultWriter): Either[ResultFailed, ResultSuccess] = {
      val startedAt = JodaDateTime.now()
      val smsg = s"Validating job-type ${opts.jobTypeId.id} ${opts.toString} in ${resource.path.toString}"
      logger.info(smsg)

      validate(opts, resource.jobId, writer) match {
        case Some(r) => Left(r)
        case _ =>
          // This needs to be blocking
          updateStateToRunning(resource.jobId)
          opts.toJob.run(resource, writer, dao, eventManager) match {
          case Left(a) => Left(a)
          case Right(_) =>
            val runTime = computeTimeDelta(JodaDateTime.now(), startedAt)
            Right(ResultSuccess(resource.jobId, opts.jobTypeId.id, "Successfully", runTime, AnalysisJobStates.FAILED, host))
        }
      }
    }
  }

}
