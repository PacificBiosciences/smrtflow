package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.actors.CommonMessages._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobTypeIds
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobResourceResolver
}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.{
  JobChangeStateMessage,
  RunChangedStateMessage
}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object EngineMultiJobEventManagerActor {
  // Message Protocols

}

// The naming of this is not great. This is a more general abstraction that responses to
// General Job related events and triggers updating of state.
class EngineMultiJobEventManagerActor(dao: JobsDao,
                                      resolver: JobResourceResolver,
                                      config: SystemJobConfig)
    extends Actor
    with ActorLogging {

  import CommonModelImplicits._

  override def preStart(): Unit = {
    log.info(s"Starting MultiJob engine manager actor $self with $config")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    log.error(
      s"$self (pre-restart) Unhandled exception ${reason.getMessage} Message $message")
  }

  def andLog(sx: String): Future[String] = Future {
    log.info(sx)
    sx
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
        s"From parent job $multiJobId Found ${childJobs.length} children jobs $childJobs")
      updates <- Future.traverse(childJobs.map(_.id))(checkChildren)
    } yield
      updates
        .reduceLeftOption(_ ++ " " ++ _)
        .getOrElse(s"No Children found for $multiJobId")
  }

  def checkForNewlyImported(importedJobId: Int): Future[String] = {
    for {
      dsMetas <- dao.getDataSetMetasByJobId(importedJobId)
      updates <- Future.traverse(dsMetas.map(_.uuid))(
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
      _ <- andLog(
        "Triggering Update to recompute the state of MultiJob Job $multiJobId state")
      updatedJob <- dao.triggerUpdateOfMultiJobState(multiJobId)
    } yield
      s"Triggered update of MultiJob $multiJobId. Now in state ${updatedJob.state}"

  def logResultsMessage(fx: => Future[String]): Unit = {
    fx onComplete {
      case Success(results) =>
        log.info(s"$results")
      case Failure(ex) =>
        log.error(ex.getMessage)
    }
  }

  def triggerMultiJobStateUpdateAndLog(multiJobId: Int,
                                       fx: => Future[String]): Unit = {
    logResultsMessage(
      for {
        msg <- fx
        multiJobMsg <- triggerUpdateOfMultiJobStateFromChildren(multiJobId)
      } yield s"$msg $multiJobMsg"
    )
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
      // Not clear what filtering on MultiJob state is necessary here
      if (job.isMultiJob) {
        triggerMultiJobStateUpdateAndLog(
          job.id,
          checkAllChildrenJobsOfMultiJob(job.id))
      } else {

        // These should probably be generalized. There's really nothing fundamental about filtering by Job Type
        if (job.jobTypeId == JobTypeIds.IMPORT_DATASET.id) {
          //FIXME. This might need an explicit trigger a MultiJob state Update (if necessary)
          // This might need to return the MultiJob Ids of the Children Jobs that were updated
          // Currently, the Child job (if updated state to SUBMITTED) will trigger the parent MultiJob to be updated
          logResultsMessage(checkForNewlyImported(job.id))
        }

        // If a Child Job has changed State, then trigger the parent MultiJob to be updated
        // In practice this is only on pbsmrtpipe job type
        job.parentMultiJobId match {
          case Some(multiJobId) =>
            logResultsMessage(
              triggerUpdateOfMultiJobStateFromChildren(multiJobId))
          case _ =>
            log.debug(
              s"No parent Id detected for Job ${job.id}. Skipping updating of Parent Job")
        }
      }

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

    case x =>
      log.info(s"$self got message $x")
  }
}

trait EngineMultiJobEventManagerActorProvider {
  this: ActorRefFactoryProvider
    with JobsDaoProvider
    with SmrtLinkConfigProvider =>

  val engineMultiJobEventManagerActor: Singleton[ActorRef] =
    Singleton(
      () =>
        actorRefFactory().actorOf(
          Props(
            classOf[EngineMultiJobEventManagerActor],
            jobsDao(),
            jobResolver(),
            systemJobConfig()
          ),
          "EngineMultiJobEventManagerActor"
      ))
}
