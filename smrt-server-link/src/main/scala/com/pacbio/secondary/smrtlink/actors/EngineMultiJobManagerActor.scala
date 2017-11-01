package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{ask, pipe}
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.{IdAble, IntIdAble}
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
import scala.concurrent.Future

object EngineMultiJobManagerActor {
  case class CheckForRunnableMultiJobWork(multiJobId: IdAble)
  case class CheckForWorkSequentially(multiJobIds: List[Int])

  case object CheckMultiJobStatus
}

/**
  * Created by mkocher on 9/11/17.
  */
class EngineMultiJobManagerActor(dao: JobsDao,
                                 resolver: JobResourceResolver,
                                 config: SystemJobConfig,
                                 runner: ServiceMultiJobRunner,
                                 checkForWorkInterval: FiniteDuration)
    extends Actor
    with ActorLogging {

  import EngineMultiJobManagerActor._
  import CommonModelImplicits._

  val selfAskTimeout = 1.minute

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

  def checkForWorkById(ix: IdAble): Future[MessageResponse] = {
    dao
      .getMultiJobById(ix)
      .flatMap(engineJob => runner.runWorkflow(engineJob))
  }

  def checkForWorkByIntId(id: Int): Unit = checkForWorkById(IntIdAble(id))

  def checkForWork(): Unit = {
    dao.getNextRunnableEngineMultiJobs().foreach { jobs =>
      self ! CheckForWorkSequentially(jobs.map(_.id).toList)
    }
  }

  /**
    * Run this sequentially to throttle db usage
    *
    * @param jobIds List of MultiJobIds
    */
  def sequentiallyCheckForAllWork(jobIds: List[Int]): Unit = {
    jobIds match {
      case Nil => log.info("No multi-jobs found to update")
      case multiJobId :: Nil =>
        self ! CheckForRunnableMultiJobWork(multiJobId)
      case multiJobId :: tail =>
        (self ? CheckForRunnableMultiJobWork(multiJobId))(selfAskTimeout)
          .onComplete { _ =>
            self ! CheckForWorkSequentially(tail)
          }

    }
  }

  override def receive: Receive = {
    case CheckForRunnableJob =>
      log.info(s"$self Checking for MultiJob Work")
      checkForWork()

    case CheckForRunnableMultiJobWork(multiJobId) =>
      log.info(s"Checking for MultiJob ${multiJobId.toIdString}")
      checkForWorkById(multiJobId) pipeTo sender()

    case CheckForWorkSequentially(multiJobIds) =>
      log.info(
        s"${multiJobIds.length} MultiJobs remaining to check for updates")
      sequentiallyCheckForAllWork(multiJobIds)

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
        actorRefFactory().actorOf(
          Props(classOf[EngineMultiJobManagerActor],
                jobsDao(),
                jobResolver(),
                systemJobConfig(),
                new ServiceMultiJobRunner(jobsDao(), systemJobConfig()),
                1.minute),
          "EngineMultiJobManagerActor"
      ))
}
