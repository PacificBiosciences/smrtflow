package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.jobtypes.ServiceMultiJobRunner

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object EngineMultiJobWorkerActor {

  def props(multiJobId: Int,
            dao: JobsDao,
            runner: ServiceMultiJobRunner,
            manager: ActorRef): Props =
    Props(new EngineMultiJobWorkerActor(multiJobId, dao, runner, manager))
}

class EngineMultiJobWorkerActor(multiJobId: Int,
                                dao: JobsDao,
                                runner: ServiceMultiJobRunner,
                                manager: ActorRef)
    extends Actor
    with ActorLogging {

  import EngineMultiJobManagerActor._

  val blockingTimeOut = 2.minute

  // Schedule initial check for work
  val checkForWorkTick = context.system.scheduler
    .scheduleOnce(5.seconds, self, CheckWorkerMultiJobStatus)

  override def preStart(): Unit = {
    log.debug(s"Starting multi-worker for MultiJob $multiJobId $self")
  }

  override def postStop(): Unit = {
    log.debug(s"Shutting down worker $self")
  }

  /**
    * This needs to be a single blocking call to avoid
    * two calls being run concurrently.
    *
    * @return
    */
  def checkForUpdate(): MessageResponse = {
    Await.result(runner.runWorkflowById(multiJobId), blockingTimeOut)
  }

  def checkIfComplete() = {
    dao.getMultiJobById(multiJobId).map(_.state.isCompleted).map {
      case true =>
        log.info(s"MultiJob $multiJobId completed")
        manager ! MultiJobIsCompleted(multiJobId)
        true
      case false => false
    }
  }

  override def receive: Receive = {

    case CheckWorkerMultiJobStatus =>
      checkForUpdate()
      checkIfComplete()

    case x =>
      log.warning(s"Unhandled message $x")
  }

}
