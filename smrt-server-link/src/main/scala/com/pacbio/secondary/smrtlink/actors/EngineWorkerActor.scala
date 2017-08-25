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
import scala.util.{Try,Success,Failure}


object EngineWorkerActor {
  def props(daoActor: ActorRef, jobRunner: JobRunner, serviceRunner: ServiceJobRunner): Props = Props(new EngineWorkerActor(daoActor, jobRunner, serviceRunner))
}

class EngineWorkerActor(daoActor: ActorRef, jobRunner: JobRunner, serviceRunner: ServiceJobRunner) extends Actor with ActorLogging with timeUtils {

  val WORK_TYPE:WorkerType = StandardWorkType
  import CommonModelImplicits._

  override def preStart(): Unit = {
    log.debug(s"Starting engine-worker $self")
  }

  override def postStop(): Unit = {
    log.debug(s"Shutting down worker $self")
  }

  def receive: Receive = {
    case RunEngineJob(engineJob) => {

      val tx = Try {

        val outputDir = Paths.get(engineJob.path)
        // need to convert a engineJob.settings into ServiceJobOptions
        val opts = Converters.convertEngineToOptions(engineJob)
        // Maybe this should be encapsulated at the ServiceRunner?
        serviceRunner.run(opts, engineJob.uuid, outputDir) // this blocks and will update the file job state in the db. Need to handle error case
      }

      log.info(s"Results from ServiceRunner $tx")
      // This only needs to communicate back to the EngineManager that it's completed processing the results
      // and is free to run more work.
      // Adding some copy and paste in here get it to work mechanistically
      val message = tx match {
        case Success(rx) =>
          rx match {
            case Right(x) =>
              UpdateJobCompletedResult(x, WORK_TYPE)
            case Left(ex) =>
              val emsg = s"Failed job type ${engineJob.jobTypeId} in ${engineJob.path} Job id:${engineJob.id} uuid:${engineJob.uuid}  ${ex.message}"
              log.error(emsg)
              UpdateJobCompletedResult(ex, WORK_TYPE)
          }
        case Failure(ex) =>
          val emsg = s"Failed job type ${engineJob.jobTypeId} in ${engineJob.path} Job id:${engineJob.id} uuid:${engineJob.uuid}  ${ex.getMessage}"
          log.error(emsg)
          UpdateJobCompletedResult(ResultFailed(engineJob.uuid, engineJob.jobTypeId, emsg, 0, AnalysisJobStates.FAILED, "localhost"), WORK_TYPE)
      }

      sender ! message
    }

    case x => log.debug(s"Unhandled Message to Engine Worker $x")
  }
}

object QuickEngineWorkerActor {
  def props(daoActor: ActorRef, jobRunner: JobRunner, serviceRunner: ServiceJobRunner): Props = Props(new QuickEngineWorkerActor(daoActor, jobRunner, serviceRunner))
}

class QuickEngineWorkerActor(daoActor: ActorRef, jobRunner: JobRunner, serviceRunner: ServiceJobRunner) extends EngineWorkerActor(daoActor, jobRunner, serviceRunner){
  override val WORK_TYPE = QuickWorkType
}