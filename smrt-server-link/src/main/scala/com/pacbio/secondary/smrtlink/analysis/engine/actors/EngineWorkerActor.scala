package com.pacbio.secondary.smrtlink.analysis.engine.actors

import java.io.FileWriter
import java.net.InetAddress
import java.nio.file.{Files, Paths}

import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.analysis.engine.CommonMessages
import CommonMessages._
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._


object EngineWorkerActor {
  def props(daoActor: ActorRef, jobRunner: JobRunner): Props = Props(new EngineWorkerActor(daoActor, jobRunner))
}

class EngineWorkerActor(daoActor: ActorRef, jobRunner: JobRunner) extends Actor
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

    case x => log.debug(s"Unhandled Message to Engine Worker $x")
  }
}

object QuickEngineWorkerActor {
  def props(daoActor: ActorRef, jobRunner: JobRunner): Props = Props(new QuickEngineWorkerActor(daoActor, jobRunner))
}

class QuickEngineWorkerActor(daoActor: ActorRef, jobRunner: JobRunner) extends EngineWorkerActor(daoActor, jobRunner){
  override val WORK_TYPE = QuickWorkType
}