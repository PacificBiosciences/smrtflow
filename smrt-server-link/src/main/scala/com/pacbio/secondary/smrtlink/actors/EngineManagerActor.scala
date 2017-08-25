package com.pacbio.secondary.smrtlink.actors

import java.io.{PrintWriter, StringWriter}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.CommonMessages._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResourceResolver, SimpleAndImportJobRunner}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jobtypes.ServiceJobRunner
import com.pacbio.secondary.smrtlink.models.EngineConfig

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Created by mkocher on 8/24/17.
  *
  * Adding some hacks to adhere to the JobsDaoActor interface and get the code to compile
  */
class EngineManagerActor(dao: JobsDao, engineConfig: EngineConfig, resolver: JobResourceResolver) extends Actor with ActorLogging {

  import CommonModelImplicits._

  //MK Probably want to have better model for this
  val checkForWorkInterval = 5.seconds

  val checkForWorkTick = context.system.scheduler.schedule(5.seconds, checkForWorkInterval, self, CheckForRunnableJob)

  // Keep track of workers
  val workers = mutable.Queue[ActorRef]()

  val maxNumQuickWorkers = 10
  // For jobs that are small and can completed in a relatively short amount of time (~seconds) and have minimal resource usage
  val quickWorkers = mutable.Queue[ActorRef]()

  // This is not awesome
  val jobRunner = new SimpleAndImportJobRunner(self)
  val serviceRunner = new ServiceJobRunner(dao)

  def addJobToWorker(engineJob: EngineJob, workerQueue: mutable.Queue[ActorRef], worker: ActorRef): Unit = {
    log.info(s"Adding EngineJob ${engineJob.uuid} to worker $worker.  numWorkers ${workers.size}, numQuickWorkers ${quickWorkers.size}")
    log.info(s"Found jobOptions work ${engineJob.jobTypeId}. Updating state and starting task.")

    // This should be extended to support a list of Status Updates, to avoid another ask call and a separate db call
    // e.g., UpdateJobStatus(runnableJobWithId.job.uuid, Seq(AnalysisJobStates.SUBMITTED, AnalysisJobStates.RUNNING)
    val f:Future[String] = for {
      job <- dao.updateJobState(engineJob.id, AnalysisJobStates.RUNNING, "Updated to Running")
    } yield s"Updated state of ${job.id} type:${job.jobTypeId} to state:${job.state}"


    f onComplete {
      case Success(msg) =>
        log.info(msg)
        log.info(s"Sending worker $worker job id:${engineJob.id} uuid:${engineJob.uuid} type:${engineJob.jobTypeId}")
        worker ! RunEngineJob(engineJob)
      case Failure(ex) =>
        workerQueue.enqueue(worker)
        val emsg = s"addJobToWorker Unable to update state ${engineJob.id} Marking as Failed. Error ${ex.getMessage}"
        log.error(emsg)
        // This is a fire and forget
        dao.updateJobState(engineJob.id, AnalysisJobStates.FAILED, emsg)
    }
  }

  // This should return a future
  def checkForWork(): Unit = {
    //log.info(s"Checking for work. # of Workers ${workers.size} # Quick Workers ${quickWorkers.size} ")

    if (workers.nonEmpty) {
      val worker = workers.dequeue()
      val f = dao.getNextRunnableEngineJob()

      f onSuccess {
        case Right(engineJob) => addJobToWorker(engineJob, workers, worker)
        case Left(e) =>
          workers.enqueue(worker)
          log.debug(s"No work found. ${e.message}")
      }

      f onFailure {
        case e =>
          workers.enqueue(worker)
          log.error(s"Failure checking for new work ${e.getMessage}")
      }
    } else {
      log.debug("No available workers.")
    }

    if (quickWorkers.nonEmpty) {
      val worker = quickWorkers.dequeue()
      val f = dao.getNextRunnableEngineJob()
      f onSuccess {
        case Right(engineJob) => addJobToWorker(engineJob, quickWorkers, worker)
        case Left(e) =>
          quickWorkers.enqueue(worker)
          log.debug(s"No work found. ${e.message}")
      }
      f onFailure {
        case e =>
          quickWorkers.enqueue(worker)
          log.error(s"Failure checking for new work ${e.getMessage}")
      }
    } else {
      log.debug("No available workers.")
    }
  }

  override def preStart(): Unit = {
    log.info(s"Starting engine manager actor $self with $engineConfig")

    (0 until engineConfig.maxWorkers).foreach { x =>
      val worker = context.actorOf(EngineWorkerActor.props(self, jobRunner, serviceRunner), s"engine-worker-$x")
      workers.enqueue(worker)
      log.debug(s"Creating worker $worker")
    }

    (0 until maxNumQuickWorkers).foreach { x =>
      val worker = context.actorOf(QuickEngineWorkerActor.props(self, jobRunner, serviceRunner), s"engine-quick-worker-$x")
      quickWorkers.enqueue(worker)
      log.debug(s"Creating Quick worker $worker")
    }
  }

  override def preRestart(reason:Throwable, message:Option[Any]){
    super.preRestart(reason, message)
    log.error(s"$self (pre-restart) Unhandled exception ${reason.getMessage} Message $message")
  }


  override def receive: Receive = {
    case CheckForRunnableJob =>
      //FIXME. This Try is probably not necessary
      Try {
        checkForWork()
      } match {
        case Success(_) =>
        case Failure(ex) =>
          val sw = new StringWriter
          ex.printStackTrace(new PrintWriter(sw))
          log.error(s"Failed check for runnable jobs ${ex.getMessage}")
          log.error(sw.toString)
      }

    case UpdateJobCompletedResult(result, workerType) =>
      log.info(s"Worker $sender completed $result")
      workerType match {
        case QuickWorkType => quickWorkers.enqueue(sender)
        case StandardWorkType => workers.enqueue(sender)
      }

      val errorMessage = result.state match {
        case AnalysisJobStates.FAILED => Some(result.message)
        case _ => None
      }

      val f = dao.updateJobState(result.uuid, result.state, s"Updated to ${result.state}", errorMessage)

      f onComplete {
        case Success(_) =>
          log.info(s"Successfully updated job ${result.uuid} to ${result.state} msg:$errorMessage")
          self ! CheckForRunnableJob
        case Failure(ex) =>
          log.error(s"Failed to update job ${result.uuid} state to ${result.state} Error ${ex.getMessage}")
          self ! CheckForRunnableJob
      }

    case x => log.warning(s"Unhandled message $x to database actor.")
  }
}

trait EngineManagerActorProvider {
  this: ActorRefFactoryProvider with JobsDaoProvider with SmrtLinkConfigProvider =>

  val engineManagerActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[EngineManagerActor], jobsDao(), jobEngineConfig(), jobResolver()), "EngineManagerActor"))
}

