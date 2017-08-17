package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorRef, Props}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dataintegrity.{BaseDataIntegrity, DataSetIntegrityRunner, JobStateIntegrityRunner}


object DataIntegrityManagerActor {
  case object RunIntegrityChecks
  // Run an explicit Runner by id
  case class RunIntegrityCheckById(id: String)
}

class DataIntegrityManagerActor(dao: JobsDao, runners: Seq[BaseDataIntegrity], smrtLinkSystemVersion: Option[String]) extends Actor with LazyLogging with timeUtils{

  import DataIntegrityManagerActor._

  // If the granularity of the running needs to be on a per Task basis,
  // then this should be pushed into separate worker actors.
  context.system.scheduler.scheduleOnce(20.seconds, self, RunIntegrityChecks)

  override def preStart() = {
    logger.info(s"Starting $self with Runners:${runners.map(_.runnerId)} with SMRT Link System version $smrtLinkSystemVersion")
  }

  def andLog(m: String): String = {
    logger.info(m)
    m
  }

  private def executeRunner(runner: BaseDataIntegrity): Future [MessageResponse] = {
    val f = for {
      startedAt <- Future.successful(JodaDateTime.now())
      _ <- Future.successful(s"Starting to run ${runner.runnerId}")
      result <- runner.run() // Wrap this in a Try
      message <- Future {s"Completed running ${runner.runnerId} in ${computeTimeDelta(JodaDateTime.now(), startedAt)} sec"}
    } yield MessageResponse(s"${result.message} $message")

    f.onSuccess { case MessageResponse(m) => s"Successfully ran ${runner.runnerId} $m"}
    f.onFailure { case ex => logger.error(s"Failed run Integrity Runner ${runner.runnerId}. ${ex.getMessage}")}
    f
  }

  override def receive: Receive = {
    // We probably want to wire in a webservice layer to trigger this via a
    // POST to smrt-link/data-integrity or POST to smrt-link/data-integrity/{runner-id} which will return a MessageResponse
    // But limit to only running one runner type at a time (make it impossible to run instances of the same DataIntegrityRunner)
    case RunIntegrityChecks => {
      val successMessage = MessageResponse(s"Triggered run of SMRT Link DataIntegrity (${runners.length}) checks")
      sender ! successMessage
      // Maybe this isn't the greatest idea. This should just run serially
      runners.foreach(executeRunner)
    }
    case RunIntegrityCheckById(runnerId) =>
      runners.find(_.runnerId == runnerId) match {
        case Some(runner) =>
          sender ! MessageResponse(s"Triggered runner $runnerId")
          executeRunner(runner)
        case _ =>
          // This should use pipeTo and fail the Future
          sender ! MessageResponse(s"Failed to find runner with id $runnerId")
      }
  }
}

trait DataIntegrityManagerActorProvider {
  this: ActorRefFactoryProvider with JobsDaoProvider with SmrtLinkConfigProvider =>

  val runners: Singleton[Seq[BaseDataIntegrity]] =
    Singleton(() => Seq(
      new DataSetIntegrityRunner(jobsDao()),
      new JobStateIntegrityRunner(jobsDao(), smrtLinkVersion()))
    )

  val dataIntegrityManagerActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[DataIntegrityManagerActor], jobsDao(), runners(), smrtLinkVersion()), "DataIntegrityManagerActor"))
}
