package com.pacbio.secondary.smrtlink.actors

import java.io.{PrintWriter, StringWriter}

import akka.pattern._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.CommonMessages._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{EngineJob, EngineManagerStatus, NoAvailableWorkError}
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResourceResolver}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jobtypes.ServiceJobRunner
import com.pacbio.secondary.smrtlink.models.EngineConfig

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
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

  // For debugging
  val checkStatusForWorkTick = context.system.scheduler.schedule(5.seconds, 10.seconds, self, GetEngineManagerStatus)

  // Keep track of workers
  val workers = mutable.Queue[ActorRef]()

  // For jobs that are small and can completed in a relatively short amount of time (~seconds) and have minimal resource usage
  val quickWorkers = mutable.Queue[ActorRef]()

  val serviceRunner = new ServiceJobRunner(dao)

  def andLog(sx: String): Future[String] = Future {
    log.info(sx)
    sx
  }


  private def getManagerStatus() = {
    val n = engineConfig.maxWorkers - workers.length
    val m = engineConfig.numQuickWorkers - quickWorkers.length
    EngineManagerStatus(engineConfig.maxWorkers, n, engineConfig.numQuickWorkers, m)
  }
  /**
    * Returns a status message
    *
    * @param engineJob Engine Job to run
    * @param workerQueue Worker queue to mutate
    * @param worker worker to send EngineJob to
    * @return
    */
  def addJobToWorker(engineJob: EngineJob, workerQueue: mutable.Queue[ActorRef], worker: ActorRef): Future[String] = {
    // This should be extended to support a list of Status Updates, to avoid another ask call and a separate db call
    // e.g., UpdateJobStatus(runnableJobWithId.job.uuid, Seq(AnalysisJobStates.SUBMITTED, AnalysisJobStates.RUNNING)
    implicit val timeOut = Timeout(5.seconds)
    val f:Future[String] = for {
      job <- dao.updateJobState(engineJob.id, AnalysisJobStates.RUNNING, "Updated to Running")
      _ <- andLog(s"Updated state of ${job.id} type:${job.jobTypeId} to state:${job.state}")
      _ <- andLog(s"Sending Worker $worker job id:${engineJob.id} uuid:${engineJob.uuid} type:${engineJob.jobTypeId}")
      _ <- worker ? RunEngineJob(engineJob)
      workerMsg <- andLog(s"Started job id:${engineJob.id} type:${engineJob.jobTypeId} on Engine worker $worker")
    } yield workerMsg

    f
  }

  def checkForWorker(workerQueue: mutable.Queue[ActorRef]): Future[String] = {

    /**
      * Util to sort out the composibility with the dao engine interface
      *
      * @param et
      * @return
      */
    def workerToFuture(et: Either[NoAvailableWorkError, EngineJob]): Future[String] = {
      et match {
        case Right(engineJob) =>
          val worker = workerQueue.dequeue()
          // This needs to handle failure case and enqueue the worker
          addJobToWorker(engineJob, workers, worker)
              .recoverWith {case NonFatal(ex) =>
                val msg = s"Failed to add Job ${engineJob.id} to worker $worker"
                log.error(msg)
                  workerQueue.enqueue(worker)
                  Future.successful(s"WARNING Failed to add worker to worker. Enqueued worker to $workerQueue")
              }
        case Left(_) => Future.successful("No available work found")
      }
    }

    if (workerQueue.nonEmpty) {
      for {
        engineJobOrNoWork <- dao.getNextRunnableEngineJob()
        msg <- workerToFuture(engineJobOrNoWork)
      } yield msg
    } else {
      Future.successful(s"Worker queue (${workerQueue.length}) is full")
    }
  }

  // This should return a future
  def checkForWork(): Future[String] = {
    for {
      _ <- andLog(s"Checking for work. ${getManagerStatus().prettySummary}")
      _ <- checkForWorker(workers)
      _ <- checkForWorker(quickWorkers) // this might be better as a andThen call
    } yield s"Completed checking for work ${getManagerStatus().prettySummary}"
  }

  override def preStart(): Unit = {
    log.info(s"Starting engine manager actor $self with $engineConfig")

    (0 until engineConfig.numQuickWorkers).foreach { x =>
      val worker = context.actorOf(QuickEngineWorkerActor.props(self, serviceRunner), s"engine-quick-worker-$x")
      quickWorkers.enqueue(worker)
      log.info(s"Creating Quick worker $worker")
    }

    (0 until engineConfig.maxWorkers).foreach { x =>
      val worker = context.actorOf(EngineWorkerActor.props(self, serviceRunner), s"engine-worker-$x")
      workers.enqueue(worker)
      log.info(s"Creating worker $worker")
    }
  }

  override def preRestart(reason:Throwable, message:Option[Any]){
    super.preRestart(reason, message)
    log.error(s"$self (pre-restart) Unhandled exception ${reason.getMessage} Message $message")
  }


  override def receive: Receive = {
    case CheckForRunnableJob => {
      checkForWork() onComplete {
        case Success(_) =>
          log.debug("Completed checking for work")
        case Failure(ex) =>
          val sw = new StringWriter
          ex.printStackTrace(new PrintWriter(sw))
          log.error(s"Failed check for runnable jobs ${ex.getMessage}")
          log.error(sw.toString)
      }
    }

    case CompletedWork(worker, workerType) => {
      log.info(s"Completed worker-type:$workerType worker:$worker")
      workerType match {
        case QuickWorkType =>
          log.info(s"Enqueuing worker-type:$workerType worker:$worker")
          quickWorkers.enqueue(worker)
        case StandardWorkType =>
          log.info(s"Enqueuing worker-type:$workerType worker:$worker")
          workers.enqueue(worker)
      }

      self ! CheckForRunnableJob
    }

    case GetEngineManagerStatus => {
      val status = getManagerStatus()
      log.info(s"EngineManager status ${status.prettySummary}")
      sender ! status
    }

    case x => log.warning(s"Unhandled message $x to $self")
  }
}

trait EngineManagerActorProvider {
  this: ActorRefFactoryProvider with JobsDaoProvider with SmrtLinkConfigProvider =>

  val engineManagerActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[EngineManagerActor], jobsDao(), jobEngineConfig(), jobResolver()), "EngineManagerActor"))
}

