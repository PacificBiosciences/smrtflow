package com.pacbio.secondary.analysis.engine.actors

import java.nio.file.Files

import com.pacbio.secondary.analysis.engine.{CommonMessages, EngineConfig}
import CommonMessages._
import com.pacbio.secondary.analysis.jobs
import com.pacbio.secondary.analysis.jobs.JobModels.{JobTypeId, NoAvailableWorkError, RunnableJob, RunnableJobWithId}
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates.Completed
import com.pacbio.secondary.analysis.jobs._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable

object EngineManagerActor {

  case object ShutdownManager

  case object KillAllWorkers

}

trait EngineActorCore {
  val engineConfig: EngineConfig
  val workers: mutable.Queue[ActorRef]
  val quickWorkers: mutable.Queue[ActorRef]
  val jobRunner: JobRunner
  val resolver: JobResourceResolver
}


/**
 * This Engine Manager is the hub of adding tasks and running tasks via workers
 *
 * The manager will persist data to DataAccessLayer via DAO Actor.
 *
 * The Manager and the Dao are split to divide responsibility. The DAO can be replaced
 * with a InMemory or db driven implementation.
 *
 * @param daoActor Access point for persisting state
 */
class EngineManagerActor(val daoActor: ActorRef,
                         val engineConfig: EngineConfig,
                         val resolver: JobResourceResolver,
                         val jobRunner: JobRunner)
    extends Actor with EngineActorCore with ActorLogging {

  final val QUICK_TASK_IDS = Set(JobTypeId("import_dataset"), JobTypeId("merge_dataset"))

  implicit val timeout = Timeout(16.seconds)

  val logStatusInterval = if (engineConfig.debugMode) 1.minute else 10.minutes

  //MK Probably want to have better model for this
  val checkForWorkInterval = 8.seconds

  val checkForWorkTick = context.system.scheduler.schedule(10.seconds, checkForWorkInterval, self, CheckForRunnableJob)

  // Keep track of workers
  val workers = mutable.Queue[ActorRef]()

  val maxNumQuickWorkers = 10
  // For jobs that are small and can completed in a relatively short amount of time (~seconds) and have minimal resource usage
  val quickWorkers = mutable.Queue[ActorRef]()

  override def preStart(): Unit = {
    log.info(s"Starting manager actor $self with $engineConfig")

    (0 until engineConfig.maxWorkers).foreach { x =>
      val worker = context.actorOf(EngineWorkerActor.props(daoActor, jobRunner), s"engine-worker-$x")
      workers.enqueue(worker)
      log.debug(s"Creating worker $worker")
    }

    (0 until maxNumQuickWorkers).foreach { x =>
      val worker = context.actorOf(QuickEngineWorkerActor.props(daoActor, jobRunner), s"engine-quick-worker-$x")
      quickWorkers.enqueue(worker)
      log.debug(s"Creating Quick worker $worker")
    }
  }

  override def postStop(): Unit = {
    checkForWorkTick.cancel()
  }

  def addJobToWorker(runnableJobWithId: RunnableJobWithId, workerQueue: mutable.Queue[ActorRef]): Unit = {

    if (workerQueue.nonEmpty) {
      log.debug(s"Checking for work. Number of available Workers ${workerQueue.size}")
      log.debug(s"Found jobOptions work ${runnableJobWithId.job.jobOptions.toJob.jobTypeId}. Updating state and starting task.")

      val fx = daoActor ? UpdateJobStatus(runnableJobWithId.job.uuid, AnalysisJobStates.SUBMITTED, None)

      fx onComplete {
        case Success(_) =>
          val worker = workerQueue.dequeue()
          val outputDir = resolver.resolve(runnableJobWithId)
          worker ! RunJob(runnableJobWithId.job, outputDir)
        case Failure(ex) =>
          val msg = s"Failed to update job state of ${runnableJobWithId.job} with ${runnableJobWithId.job.uuid.toString}"
          log.error(msg)
          daoActor ! UpdateJobStatus(runnableJobWithId.job.uuid, AnalysisJobStates.FAILED, Some(msg))
      }
    }
  }

  def checkForWork(): Unit = {
    log.debug(s"Checking for work. Number of available Workers ${workers.size}")

    if (workers.nonEmpty || quickWorkers.nonEmpty) {
      val f = (daoActor ? HasNextRunnableJobWithId).mapTo[Either[NoAvailableWorkError, RunnableJobWithId]]

      f onSuccess {
        case Right(runnableJob) =>
          val jobType = runnableJob.job.jobOptions.toJob.jobTypeId
          if ((QUICK_TASK_IDS contains jobType) && quickWorkers.nonEmpty) addJobToWorker(runnableJob, quickWorkers)
          else addJobToWorker(runnableJob, workers)
        case Left(e) => log.debug(s"No work found. ${e.message}")
      }

      f onFailure {
        case e => log.error(s"Failure checking for new work ${e.getMessage}")
      }
    } else {
      log.debug("No available workers.")
    }
  }

  def receive: Receive = {

    case AddNewJob(job) =>
      val f = daoActor ? AddNewJob(job)

      f.onSuccess {
        case Right(x) =>
          sender ! x
          self ! CheckForRunnableJob
        case Left(e) =>
          sender ! FailedMessage(s"Failed to add jobOptions $job. Error ${e.toString}")
          self ! CheckForRunnableJob
      }

      f.onFailure {
        case e =>
          sender ! FailedMessage(s"Failed to add jobOptions $job. Error ${e.toString}")
          self ! CheckForRunnableJob
      }


    case CheckForRunnableJob =>
      //FIXME. This is probably not necessary
      Try { checkForWork() } match {
        case Success(_) =>
        case Failure(ex) => log.error(s"Failed check for runnable jobs ${ex.getMessage}")
      }

    case UpdateJobCompletedResult(result, workerType) =>
      // This should have a success/failure
      result.state match {
        case x: Completed =>

          // Only propagate messages from Failed Jobs
          val msg = x match {
            case AnalysisJobStates.FAILED => Some(result.message)
            case _  => None
          }

          daoActor ! UpdateJobStatus(result.uuid, result.state, msg)

          workerType match {
            case QuickWorkType => quickWorkers.enqueue(sender)
            case StandardWorkType => workers.enqueue(sender)
          }
          self ! CheckForRunnableJob
        case sx => log.error(s"state $sx MUST be a completed state. Got $result")
          workerType match {
            case QuickWorkType => quickWorkers.enqueue(sender)
            case StandardWorkType => workers.enqueue(sender)
          }
          self ! CheckForRunnableJob
      }

    case x => log.debug(s"Unhandled Message to Engine Message $x")
  }
}
