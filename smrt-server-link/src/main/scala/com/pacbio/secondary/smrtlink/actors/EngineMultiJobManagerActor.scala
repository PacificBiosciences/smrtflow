package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
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
import com.pacbio.secondary.smrtlink.models.MultiJobSubmitted

import scala.concurrent.duration._
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global

object EngineMultiJobManagerActor {

  case object CheckForNewMultiJobs

  case object CheckUpdateAllWorkers
  case class CheckForRunnableMultiJobWork(multiJobId: IdAble)
  case class CheckForWorkSequentially(multiJobIds: List[Int])

  case object CheckWorkerMultiJobStatus
  case class MultiJobIsCompleted(multiJobId: Int)
  case class CreateMultiJobWorker(multiJobId: Int)
}

/**
  * The general model is to offload the processing to a worker (Actor) per MultiJob
  * to avoid collisions or different threads processing the same MultiJob.
  *
  * This is also starting to lay the foundation for a more event-driven model. (i.e.,
  * core job 1234 has completed, each actor could listen and take the necessary
  * action. This should reduce unnecessary db calls)
  *
  */
class EngineMultiJobManagerActor(dao: JobsDao,
                                 resolver: JobResourceResolver,
                                 config: SystemJobConfig,
                                 runner: ServiceMultiJobRunner,
                                 multiJobWorkCheckDuration: FiniteDuration,
                                 workerCheckDuration: FiniteDuration)
    extends Actor
    with ActorLogging {

  import EngineMultiJobManagerActor._
  import CommonModelImplicits._

  val workers = TrieMap.empty[Int, ActorRef]

  val startUpDelay = 5.seconds

  // Trigger to check for new MultiJobs
  val checkForNewWorkTick = context.system.scheduler.schedule(
    startUpDelay,
    multiJobWorkCheckDuration,
    self,
    CheckForNewMultiJobs)

  // Trigger WORKERS to Check for Updates
  val checkWorkersForUpdatesTick = context.system.scheduler.schedule(
    startUpDelay * 2,
    workerCheckDuration,
    self,
    CheckUpdateAllWorkers)

  override def preStart(): Unit = {
    log.info(s"Starting engine manager actor $self with $config")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    log.error(
      s"$self (pre-restart) Unhandled exception ${reason.getMessage} Message $message")
  }

  def addAndCreateWorker(multiJobId: Int): Unit = {
    if (!(workers contains multiJobId)) {
      val worker = context.actorOf(
        EngineMultiJobWorkerActor.props(multiJobId, dao, runner, self))
      log.info(
        s"Created MultiJob Worker for MultiJob $multiJobId worker:$worker")
      workers(multiJobId) = worker
    }
  }

  def checkForNewMultiJobs(): Unit = {
    dao
      .getNextRunnableEngineMultiJobs()
      .map { jobs =>
        jobs.map(_.id).toList.filter(i => !(workers.keySet contains i))
      }
      .foreach { jobIds =>
        jobIds.foreach { jobId =>
          self ! CreateMultiJobWorker(jobId)
        }
      }
  }

  def removeWorker(multiJobId: Int) = {
    if (workers contains multiJobId) {
      log.info(s"Removing MultiJob $multiJobId and shutting down worker")
      workers - multiJobId
    }
  }

  override def receive: Receive = {

    case MultiJobSubmitted(multiJobId) =>
      log.info(s"Custom trigger for MultiJob $multiJobId")
      self ! CheckForNewMultiJobs

    case CreateMultiJobWorker(multiJobId) =>
      addAndCreateWorker(multiJobId)

    case MultiJobIsCompleted(multiJobId) =>
      removeWorker(multiJobId)
      sender ! PoisonPill

    case CheckForNewMultiJobs =>
      checkForNewMultiJobs()

    case CheckUpdateAllWorkers =>
      // This should probably have a cascading sequential call to each worker
      workers.values.toList.foreach { worker =>
        worker ! CheckWorkerMultiJobStatus
      }

    //case x => log.warning(s"Unsupported message $x to $self")
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
          Props(
            classOf[EngineMultiJobManagerActor],
            jobsDao(),
            jobResolver(),
            systemJobConfig(),
            new ServiceMultiJobRunner(jobsDao(), systemJobConfig()),
            multiJobPoll(),
            multiJobWorkerPoll()
          ),
          "EngineMultiJobManagerActor"
      ))
}
