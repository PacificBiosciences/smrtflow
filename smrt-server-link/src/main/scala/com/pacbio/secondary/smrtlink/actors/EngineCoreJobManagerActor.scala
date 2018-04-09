package com.pacbio.secondary.smrtlink.actors

import java.io.{PrintWriter, StringWriter}

import akka.pattern._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.CommonMessages._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  EngineManagerStatus,
  NoAvailableWorkError
}
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobResourceResolver
}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jobtypes.ServiceJobRunner
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.JobChangeStateMessage

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Created by mkocher on 8/24/17.
  *
  * Adding some hacks to adhere to the JobsDaoActor interface and get the code to compile
  */
class EngineCoreJobManagerActor(dao: JobsDao,
                                resolver: JobResourceResolver,
                                config: SystemJobConfig)
    extends Actor
    with ActorLogging {

  import CommonModelImplicits._

  // The core model for this is to listen for Job state changes from CREATED to SUBMITTED.
  val checkForWorkInterval = 30.seconds

  val checkForWorkTick = context.system.scheduler.schedule(
    5.seconds,
    checkForWorkInterval,
    self,
    CheckForRunnableJob)

  // For debugging
  val checkForEngineManagerStatusInterval = 10.minutes

  val checkStatusForWorkTick = context.system.scheduler.schedule(
    5.seconds,
    checkForEngineManagerStatusInterval,
    self,
    GetEngineManagerStatus)

  // Keep track of workers
  val workers = mutable.Queue[ActorRef]()

  // For jobs that are small and can completed in a relatively short amount of time (~seconds) and have minimal resource usage
  val quickWorkers = mutable.Queue[ActorRef]()

  //FIXME(mpkocher)(2017-12-17) This might be better to be more grainular
  val ec = scala.concurrent.ExecutionContext.Implicits.global
  val serviceRunner = new ServiceJobRunner(dao, config)(ec)

  def andLog(sx: String): Future[String] = Future.successful {
    log.info(sx)
    sx
  }

  private def getManagerStatus(): EngineManagerStatus = {
    val n = config.numGeneralWorkers - workers.length
    val m = config.numQuickWorkers - quickWorkers.length
    EngineManagerStatus(config.numGeneralWorkers, n, config.numQuickWorkers, m)
  }

  /**
    * Returns a status message
    *
    * @param engineJob Engine Job to run
    * @param workerQueue Worker queue to mutate
    * @param worker worker to send EngineJob to
    * @return
    */
  def addJobToWorker(
      engineJob: EngineJob,
      workerQueue: mutable.Queue[ActorRef],
      worker: ActorRef,
      workerTimeOut: FiniteDuration = 15.seconds): Future[String] = {
    // This should be extended to support a list of Status Updates, to avoid another ask call and a separate db call
    // e.g., UpdateJobStatus(runnableJobWithId.job.uuid, Seq(AnalysisJobStates.SUBMITTED, AnalysisJobStates.RUNNING)
    implicit val timeOut = Timeout(workerTimeOut)

    val f: Future[String] = for {
      _ <- andLog(
        s"Sending Worker $worker job id:${engineJob.id} uuid:${engineJob.uuid} type:${engineJob.jobTypeId} state:${engineJob.state}")
      _ <- worker ? RunEngineJob(engineJob)
      workerMsg <- andLog(
        s"Started job id:${engineJob.id} type:${engineJob.jobTypeId} on Engine worker $worker")
    } yield workerMsg

    f
  }

  def checkForWorker(
      workerQueue: mutable.Queue[ActorRef],
      isQuick: Boolean): Future[Either[NoAvailableWorkError, EngineJob]] = {

    /**
      * Util to sort out the compatibility with the dao engine interface
      *
      * @param et Either results from checking for work
      * @return
      */
    def workerToFuture(et: Either[NoAvailableWorkError, EngineJob])
      : Future[Either[NoAvailableWorkError, EngineJob]] = {
      et match {
        case Right(engineJob) => {

          val worker = workerQueue.dequeue()
          log.info(
            s"Attempting to add job id:${engineJob.id} type:${engineJob.jobTypeId} state:${engineJob.state} to worker $worker")

          // This needs to handle failure case and enqueue the worker
          val f = addJobToWorker(engineJob, workers, worker)
            .map(_ => Right(engineJob))
            .recoverWith {
              case NonFatal(ex) =>
                val msg =
                  s"Failed to add Job ${engineJob.id} jobtype:${engineJob.jobTypeId} to worker $worker ${ex.getMessage}"
                log.error(msg)
                workerQueue.enqueue(worker)
                // Not sure exactly what should return from here? This should update the db to make sure the engine job
                // is marked as failed.
                Future.successful(Left(NoAvailableWorkError(
                  s"WARNING Failed to add worker to worker. Enqueued worker to $workerQueue ${ex.getMessage} ")))
            }
          f
        }
        case Left(_) =>
          Future.successful(Left(NoAvailableWorkError("No work found")))
      }
    }

    if (workerQueue.nonEmpty) {
      for {
        engineJobOrNoWork <- dao.getNextRunnableEngineCoreJob(isQuick)
        msg <- workerToFuture(engineJobOrNoWork)
      } yield engineJobOrNoWork
    } else {
      Future.successful(Left(
        NoAvailableWorkError(s"Worker queue (${workerQueue.length}) is full")))
    }
  }

  // This should return a future
  def checkForWork(timeout: FiniteDuration = 30.seconds): String = {
    // This must be blocking so that a different workers don't
    // pull the same job.
    val x1 = Await.result(checkForWorker(quickWorkers, true), timeout)
    val x2 = Await.result(checkForWorker(workers, false), timeout)

    val msg =
      s"Completed checking for work ${getManagerStatus().prettySummary}"
    //log.debug(msg)

    // If either worker found work to run, then send a self check for work message
    // to attempt to continue to process other work in queue
    if (x1.isRight || x2.isRight) {
      self ! CheckForRunnableJob
    }
    msg
  }

  override def preStart(): Unit = {
    log.info(s"Starting engine manager actor $self with $config")

    (0 until config.numQuickWorkers).foreach { x =>
      val worker = context.actorOf(
        QuickEngineCoreJobWorkerActor.props(self, serviceRunner),
        s"engine-quick-worker-$x")
      quickWorkers.enqueue(worker)
      log.info(s"Creating Quick worker $worker")
    }

    (0 until config.numGeneralWorkers).foreach { x =>
      val worker =
        context.actorOf(EngineCoreJobWorkerActor.props(self, serviceRunner),
                        s"engine-worker-$x")
      workers.enqueue(worker)
      log.info(s"Creating worker $worker")
    }
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    log.error(
      s"$self (pre-restart) Unhandled exception ${reason.getMessage} Message $message")
  }

  override def receive: Receive = {
    case JobChangeStateMessage(job) =>
      if (job.state == AnalysisJobStates.SUBMITTED) {
        log.info(
          s"Triggered check for more work from Job:${job.id} in state ${job.state}")
        self ! CheckForRunnableJob
      }

    case CheckForRunnableJob => {
      Try(checkForWork()) match {
        case Success(msg) =>
        //log.debug(s"Completed checking for work $msg")
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
      self ! GetEngineManagerStatus
    }

    case GetEngineManagerStatus => {
      val status = getManagerStatus()
      log.info(s"EngineManager status ${status.prettySummary}")
      sender ! status
    }

    case x => log.warning(s"Unhandled message $x to $self")
  }
}

trait EngineCoreJobManagerActorProvider {
  this: ActorRefFactoryProvider
    with JobsDaoProvider
    with SmrtLinkConfigProvider =>

  val engineManagerActor: Singleton[ActorRef] =
    Singleton(
      () =>
        actorRefFactory().actorOf(Props(classOf[EngineCoreJobManagerActor],
                                        jobsDao(),
                                        jobResolver(),
                                        systemJobConfig()),
                                  "EngineCoreJobManagerActor"))
}
