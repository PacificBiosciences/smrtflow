package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.actors.CommonMessages._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobResourceResolver
}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jobtypes.ServiceMultiJobRunner
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by mkocher on 9/11/17.
  */
class EngineMultiJobManagerActor(dao: JobsDao,
                                 resolver: JobResourceResolver,
                                 config: SystemJobConfig)
    extends Actor
    with ActorLogging {

  import CommonModelImplicits._

  val runner = new ServiceMultiJobRunner(dao, config)

  //MK Probably want to have better model for this
  val checkForWorkInterval = 5.seconds

  val checkForWorkTick = context.system.scheduler.schedule(
    5.seconds,
    checkForWorkInterval,
    self,
    CheckForRunnableJob)

  override def preStart(): Unit = {
    log.info(s"Starting engine manager actor $self with $config")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    log.error(
      s"$self (pre-restart) Unhandled exception ${reason.getMessage} Message $message")
  }

  def checkForWorkById(ix: IdAble) = {
    dao
      .getMultiJobById(ix)
      .map(engineJob => runner.runWorkflow(engineJob))
      .onFailure {
        case ex =>
          log.error(
            s"Failed to check for Multi-Job ${ix.toIdString} ${ex.getMessage}")
      }
  }

  def checkForWork(): Unit = {
    dao
      .getNextRunnableEngineMultiJobs()
      .map(jobs => jobs.map(_.id))
      .map(ids => ids.map(checkForWorkById(_)))
  }

  override def receive: Receive = {
    case CheckForRunnableJob =>
      //log.info(s"$self Checking for MultiJob Work")
      checkForWork()

    case x =>
      log.warning(s"Unsupported message $x to $self")
  }
}

trait EngineMultiJobManagerActorProvider {
  this: ActorRefFactoryProvider
    with JobsDaoProvider
    with SmrtLinkConfigProvider =>

  val engineMultiJobManagerActor: Singleton[ActorRef] =
    Singleton(
      () =>
        actorRefFactory().actorOf(Props(classOf[EngineMultiJobManagerActor],
                                        jobsDao(),
                                        jobResolver(),
                                        systemJobConfig()),
                                  "EngineMultiJobManagerActor"))
}
