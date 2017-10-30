package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorLogging}
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.jobtypes.ServiceMultiJobRunner

import scala.concurrent.ExecutionContext.Implicits.global

class EngineMultiJobWorkerActor(multiJobId: Int,
                                runner: ServiceMultiJobRunner,
                                dao: JobsDao)
    extends Actor
    with ActorLogging
    with timeUtils {

  import EngineMultiJobManagerActor._

  override def preStart(): Unit = {
    log.info(s"Starting worker $self to process MultiJob $multiJobId")
  }

  def processMultiJob(): Unit = {
    dao
      .getMultiJobById(multiJobId)
      .map(job => runner.runWorkflow(job))
  }

  override def receive: Receive = {
    case CheckMultiJobStatus =>
      processMultiJob()

    case x => log.warning(s"Unsupported message $x")
  }
}
