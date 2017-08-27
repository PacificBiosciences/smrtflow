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

      sender ! StartingWork
      // All functionality should be encapsulated here. We shouldn't even really handle the Failure case of the Try a in here
      // Within this runEngineJob, it should handle all updating of state on failure
      log.info(s"Worker $self attempting to run $engineJob")
      val tx = Try { serviceRunner.runEngineJob(engineJob)} // this blocks

      log.info(s"Worker $self Results from ServiceRunner $tx")

      val message = tx match {
        case Success(rx) => CompletedWork(WORK_TYPE)
        case Failure(ex) => CompletedWork(WORK_TYPE)
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