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
  JobTypeIds,
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
import com.pacbio.secondary.smrtlink.models.{
  JobChangeStateMessage,
  RunChangedStateMessage
}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}
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
  val checkForWorkInterval = 60.seconds

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

  //FIXME(mpkocher)(2017-12-17) This might be better to be more granular
  val ec = scala.concurrent.ExecutionContext.Implicits.global
  //val ec: ExecutionContext = context.dispatcher
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
        _ <- workerToFuture(engineJobOrNoWork)
      } yield engineJobOrNoWork
    } else {
      Future.successful(Left(
        NoAvailableWorkError(s"Worker queue (${workerQueue.length}) is full")))
    }
  }

  // This should return a future
  def checkForWork(timeout: FiniteDuration = 30.seconds): String = {
    // This must be blocking so that a different workers don't
    // pull the same job. I expected this to be handled by postgres
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

  def submitMultiJobIfCreated(multiJobId: Int): Future[String] = {

    val submitMessage = s"Updating state to SUBMITTED from $self"

    def submitter(state: AnalysisJobStates.JobStates): Future[String] = {
      state match {
        case AnalysisJobStates.CREATED =>
          dao
            .updateJobState(multiJobId,
                            AnalysisJobStates.SUBMITTED,
                            submitMessage,
                            None)
            .map(_ => submitMessage)
        case sx =>
          Future.successful(
            s"No action taken. Job $multiJobId state:$sx must be in CREATED state")
      }
    }

    dao.getMultiJobById(multiJobId).flatMap(j => submitter(j.state))
  }

  def checkChildren(childId: Int): Future[String] = {
    for {
      _ <- andLog(s"Checking for Child Job $childId")
      msg <- dao.checkCreatedChildJobForResolvedEntryPoints(childId)
      _ <- andLog(msg)
    } yield msg
  }

  def checkAllChildrenJobsOfMultiJob(multiJobId: Int): Future[String] = {
    for {
      childJobs <- dao.getMultiJobChildren(multiJobId)
      _ <- andLog(
        s"From parent MultiJob:$multiJobId Found ${childJobs.length} children jobs $childJobs")
      updates <- Future.traverse(childJobs.map(_.id).distinct)(checkChildren)
    } yield
      updates
        .reduceLeftOption(_ ++ " " ++ _)
        .getOrElse(s"No Children found for MultiJob:$multiJobId")
  }

  def checkForNewlyImported(importedJobId: Int): Future[String] = {
    for {
      dsMetas <- dao.getDataSetMetasByJobId(importedJobId)
      updates <- Future.traverse(dsMetas.map(_.uuid).distinct)(
        dao.checkCreatedChildrenJobsForNewlyEnteredDataSet)
    } yield updates.reduceLeftOption(_ ++ _).getOrElse("No Found Updates.")
  }

  /**
    * Recompute the MultiJob state from the children job states.
    *
    * Only a MultiJob in a non-terminal state will be updated.
    *
    * @param multiJobId MultiJob Id
    * @return
    */
  def triggerUpdateOfMultiJobStateFromChildren(
      multiJobId: Int): Future[String] =
    for {
      nowJob <- dao.getMultiJobById(multiJobId)
      _ <- andLog(
        s"Triggering Update to recompute the state of MultiJob Job $multiJobId state (current state:${nowJob.state})")
      updatedJob <- dao.triggerUpdateOfMultiJobState(multiJobId)
    } yield
      s"Triggered update of MultiJob $multiJobId. Updated state ${updatedJob.state}"

  def logResultsMessage(fx: => Future[String]): Unit = {
    fx onComplete {
      case Success(results) =>
        log.info(s"$results")
      case Failure(ex) =>
        log.error(ex.getMessage)
    }
  }

  private def onJobRunner(
      f: EngineJob => Boolean,
      fx: => Future[String]): EngineJob => Future[String] = { job =>
    if (f(job)) {
      fx
    } else {
      Future.successful("")
    }
  }

  def onJobChangeCheckIfParentJob(job: EngineJob): Future[String] = {
    job.parentMultiJobId match {
      case Some(multiJobId) =>
        triggerUpdateOfMultiJobStateFromChildren(multiJobId)
      case _ => Future.successful("")
    }
  }

  def onJobChangeCheckForImportedJob(job: EngineJob): Future[String] = {
    def jobFilter(j: EngineJob): Boolean = {
      (j.jobTypeId == JobTypeIds.IMPORT_DATASET.id) && (j.state == AnalysisJobStates.SUCCESSFUL)
    }
    onJobRunner(jobFilter, checkForNewlyImported(job.id))(job)
  }

  def onJobChangeCheckForMultiJob(job: EngineJob): Future[String] = {
    def jobFilter(j: EngineJob): Boolean = {
      // Are other states useful to trigger children updates?
      j.isMultiJob && (j.state == AnalysisJobStates.SUBMITTED)
    }

    onJobRunner(jobFilter, checkAllChildrenJobsOfMultiJob(job.id))(job)
  }

  override def receive: Receive = {

    /**
      * Handle three core cases here
      *
      * 1. MultiJob was created. Then trigger a look to see if all the entry points are resolved for each child
      * job. If so that change the child job state to SUBMITTED and then update trigger a MultiJob Update.
      *
      * 2. Import-dataset job. Find output dataset and see if this is an
      * input to a multi-job that is in the created state
      *
      * 3. pbsmrtpipe job. See if Child job is from a MultiJob and update MultiJob state (if necessary)
      *
      */
    case JobChangeStateMessage(job) =>
      log.info(
        s"Got Job changed state Job:${job.id} type:${job.jobTypeId} state:${job.state}")

      // By design this is done in cascading manner, Each onChangeX call should be very
      // specific filtering on the EngineJob metadata
      val fx = for {
        m1 <- onJobChangeCheckForImportedJob(job)
        m2 <- onJobChangeCheckForMultiJob(job)
        m3 <- onJobChangeCheckIfParentJob(job)
      } yield s"$m1 $m2 $m3"

      // Using a ask to self is a bad idea, this will potentially create dead locks.
      fx.foreach { _ =>
        if ((job.state == AnalysisJobStates.SUBMITTED) && !job.isMultiJob) {
          self ! CheckForRunnableJob
        }
      }

      logResultsMessage(fx)

    /**
      * Listen for events from Run related state changes
      *
      * Provided the Run hasn't FAILED
      *
      * 1. If reserved = true and current MultiJob state is CREATED, then update to "SUBMITTED"
      * 2. Listen for CollectionFailure Collection(uuid: X, multiJobId: X). If the Collection and the companion SubreadSet
      * will never be imported. In this case, update the Child Job state to FAILED with a specific error message.
      * */
    case RunChangedStateMessage(runSummary) =>
      runSummary.multiJobId match {
        case Some(multiJobId) =>
          logResultsMessage(
            for {
              m1 <- submitMultiJobIfCreated(multiJobId)
              m2 <- checkAllChildrenJobsOfMultiJob(multiJobId)
            } yield s"$m1 $m2"
          )
        // Need to very carefully handle the Success and Failure cases here
        case _ =>
          log.debug(
            "Run without MultiJob. Skipping MultiJob analysis Updating.")
      }

    case CheckForRunnableJob => {
      // This blocks. :(
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

    case e: EngineManagerStatus =>
      log.debug(s"Manager Status $e")

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
