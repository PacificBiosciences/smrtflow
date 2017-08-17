package com.pacbio.secondary.smrtlink.actors

import java.io.FileWriter
import java.nio.file.Paths

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.pacbio.common.models.CommonModelImplicits
import CommonMessages._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.jobtypes.{Converters, ServiceJobRunner}
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.ExecutionContext.Implicits.global


object EngineWorkerActor {
  def props(daoActor: ActorRef, jobRunner: JobRunner, serviceRunner: ServiceJobRunner): Props = Props(new EngineWorkerActor(daoActor, jobRunner, serviceRunner))
}

class EngineWorkerActor(daoActor: ActorRef, jobRunner: JobRunner, serviceRunner: ServiceJobRunner) extends Actor
with ActorLogging
with timeUtils {

  val WORK_TYPE:WorkerType = StandardWorkType
  import CommonModelImplicits._

  override def preStart(): Unit = {
    log.debug(s"Starting engine-worker $self")
  }

  override def postStop(): Unit = {
    log.debug(s"Shutting down worker $self")
  }

  def receive: Receive = {
    case RunJob(job, outputDir) =>
      val jobTypeId = job.jobOptions.toJob.jobTypeId
      val msg = s"Worker $self running job type ${jobTypeId.id} Job: $job"
      log.info(msg)

      // This would be nice to remove and to make the worker really dumb and simple.
      val pJob = JobResource(job.uuid, outputDir, AnalysisJobStates.RUNNING)
      // This might not be the best message
      sender ! UpdateJobState(job.uuid, AnalysisJobStates.RUNNING, msg, None)

      val stderrFw = new FileWriter(outputDir.resolve("pbscala-job.stderr").toAbsolutePath.toString, true)
      val stdoutFw = new FileWriter(outputDir.resolve("pbscala-job.stdout").toAbsolutePath.toString, true)
      val jobResultsWriter = new FileJobResultsWriter(stdoutFw, stderrFw)

      // This will block
      // This should only return Either[Failure, Success]
      // and then update the Job State. The JobRunner abstraction
      // should handle everything else (e.g., write task-report.json)
      val result = jobRunner.runJobFromOpts(job.jobOptions, pJob, jobResultsWriter)

      val message = result match {
        case Right(x) =>
          UpdateJobCompletedResult(x, WORK_TYPE)
        case Left(ex) =>
          val emsg = s"Failed job type ${jobTypeId.id} in ${outputDir.toAbsolutePath} Job: $job  ${ex.message}"
          log.error(emsg)
          UpdateJobCompletedResult(ex, WORK_TYPE)
      }

      stderrFw.close()
      stdoutFw.close()
      // Send Message back to EngineManager
      sender ! message

    case RunEngineJob(engineJob) =>

      val outputDir = Paths.get(engineJob.path)
      // need to convert a engineJob.settings into ServiceJobOptions
      val opts = Converters.convertEngineToOptions(engineJob) // Maybe this should be encapsulated at the ServiceRunner?
      val result = serviceRunner.run(opts, engineJob.uuid, outputDir) // this blocks

      log.info(s"Results from ServiceRunner $result")
      // This only needs to communicate back to the EngineManager that it's completed processing the results
      // and is free to run more work.
      // Adding some copy and paste in here get it to work mechanistically
      val message = result match {
        case Right(x) =>
          UpdateJobCompletedResult(x, WORK_TYPE)
        case Left(ex) =>
          val emsg = s"Failed job type ${opts.jobTypeId.id} in ${outputDir.toAbsolutePath} Job id:${engineJob.id} uuid:${engineJob.uuid}  ${ex.message}"
          log.error(emsg)
          UpdateJobCompletedResult(ex, WORK_TYPE)
      }

      // Send Message back to EngineManager
      sender ! message


    case x => log.debug(s"Unhandled Message to Engine Worker $x")
  }
}

object QuickEngineWorkerActor {
  def props(daoActor: ActorRef, jobRunner: JobRunner, serviceRunner: ServiceJobRunner): Props = Props(new QuickEngineWorkerActor(daoActor, jobRunner, serviceRunner))
}

class QuickEngineWorkerActor(daoActor: ActorRef, jobRunner: JobRunner, serviceRunner: ServiceJobRunner) extends EngineWorkerActor(daoActor, jobRunner, serviceRunner){
  override val WORK_TYPE = QuickWorkType
}