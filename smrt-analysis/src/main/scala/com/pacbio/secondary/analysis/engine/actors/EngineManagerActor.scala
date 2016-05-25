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
class EngineManagerActor(daoActor: ActorRef, engineConfig: EngineConfig, resolver: JobResourceResolver, jobRunner: JobRunner)
  extends Actor with ActorLogging {

  final val QUICK_TASK_IDS = Set(JobTypeId("import_dataset"), JobTypeId("merge_dataset"))

  implicit val timeout = Timeout(5.second)

  val logStatusInterval = if (engineConfig.debugMode) 1.minute else 10.minutes

  //MK Probably want to have better model for this
  val checkForWorkInterval = 2.seconds

  val checkForWorkTick = context.system.scheduler.schedule(10.seconds, checkForWorkInterval, self, CheckForRunnableJob)

  // Log the job summary. This should probably be in a health agent
  val tick = context.system.scheduler.schedule(10.seconds, logStatusInterval, daoActor, GetSystemJobSummary)

  //val resolver = new SimpleUUIDJobResolver(Files.createTempDirectory("engine-manager-"))
  // Keep track of workers
  val workers = mutable.Queue[ActorRef]()

  // For jobs that are small and can completed in a relatively short amount of time (~seconds)
  val quickWorkers = mutable.Queue[ActorRef]()

  override def preStart(): Unit = {
    log.info(s"Starting manager actor $self with $engineConfig")

    (0 until engineConfig.maxWorkers).foreach { x =>
      val worker = context.actorOf(EngineWorkerActor.props(daoActor, jobRunner), s"engine-worker-$x")
      workers.enqueue(worker)
      log.debug(s"Creating worker $worker")
    }
    log.info(s"Created ${workers.size} engine workers")

  }

  override def postStop(): Unit = {
    tick.cancel()
    checkForWorkTick.cancel()
  }

  def checkForWork(): Unit = {
    log.debug(s"Checking for work. Number of available Workers ${workers.size}")

    if (workers.nonEmpty) {
      val f = (daoActor ? HasNextRunnableJobWithId).mapTo[Either[NoAvailableWorkError, RunnableJobWithId]]

      f onSuccess {
        case Right(runnableJob) =>
          if (workers.nonEmpty) {
            log.debug(s"Checking for work. Number of available Workers ${workers.size}")
            log.debug(s"Found jobOptions work ${runnableJob.job.jobOptions.toJob.jobTypeId}. Updating state and starting task.")

            val fx = for {
              f1 <- daoActor ? UpdateJobStatus(runnableJob.job.uuid, AnalysisJobStates.SUBMITTED)
              f2 <- daoActor ? UpdateJobStatus(runnableJob.job.uuid, AnalysisJobStates.RUNNING)
            } yield f2

            fx onComplete {
              case Success(_) =>
                val worker = workers.dequeue()
                val outputDir = resolver.resolve(runnableJob)
                // Update jobOptions output dir
                daoActor ! UpdateJobOutputDir(runnableJob.job.uuid, outputDir)
                worker ! RunJob(runnableJob.job, outputDir)
              case Failure(ex) =>
                log.error(s"Failed to update job state of ${runnableJob.job} with ${runnableJob.job.uuid.toString}")
                val worker = workers.dequeue()
                daoActor ! UpdateJobStatus(runnableJob.job.uuid, AnalysisJobStates.FAILED)
            }
          }
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

    case UpdateJobCompletedResult(result) =>
      // This should have a success/failure
      result.state match {
        case x: Completed =>
          daoActor ! UpdateJobStatus(result.uuid, result.state)
          workers.enqueue(sender)
          self ! CheckForRunnableJob
        case x => log.error(s"state must be a completed state. Got $result")
          workers.enqueue(sender)
          self ! CheckForRunnableJob
      }

    case UpdateJobStatus(uuid, state) =>
      // FIXME. handle completed states differently
      daoActor ! UpdateJobStatus(uuid, state)
      self ! CheckForRunnableJob

    case x => log.debug(s"Unhandled Message to Engine Message $x")
  }
}
