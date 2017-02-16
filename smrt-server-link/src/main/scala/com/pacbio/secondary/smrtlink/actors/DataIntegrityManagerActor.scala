package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.dataintegrity.{BaseDataIntegrity, DataSetIntegrityRunner}
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}


object DataIntegrityManagerActor {
  case object RunIntegrityChecks
}

class DataIntegrityManagerActor(dao: JobsDao, runners: Seq[BaseDataIntegrity], interval: FiniteDuration) extends Actor with LazyLogging with timeUtils{

  import DataIntegrityManagerActor._

  // If the granularity of the running needs to be on a per Task basis,
  // then this should be pushed into separate worker actors.
  context.system.scheduler.schedule(20.seconds, interval, self, RunIntegrityChecks)

  override def preStart() = {
    logger.info(s"Starting $self with Runners:${runners.map(_.runnerId)} and interval $interval")
  }

  private def executeRunner(r: BaseDataIntegrity): Future [MessageResponse] = {
    for {
      startedAt <- Future { JodaDateTime.now()}
      result <- r.run() // Wrap this in a Try
      message <- Future {s"Completed running ${r.runnerId} in ${computeTimeDelta(JodaDateTime.now(), startedAt)} sec"}
    } yield MessageResponse(s"${result.message} $message")
  }

  override def receive: Receive = {
    // We probably want to wire in a webservice layer to trigger this via a
    // POST to smrt-link/data-integrity or POST to smrt-link/data-integrity/{runner-id} which will return a MessageResponse
    case RunIntegrityChecks => {
      val successMessage = "Triggered run of SMRT Link DataIntegrity checks"
      logger.info(successMessage)
      sender ! successMessage
      // Maybe this isn't the greatest idea. This should just run serially
      runners.foreach(executeRunner)
    }
  }
}

trait DataIntegrityManagerActorProvider {
  this: ActorRefFactoryProvider with JobsDaoProvider =>

  val runners: Singleton[Seq[BaseDataIntegrity]] =
    Singleton(() => Seq(new DataSetIntegrityRunner(jobsDao())))

  // This is for local testing. This needs to be put in the application.conf file
  // and set to a reasonable default of once or twice a day.
  val dataIntegrityInterval = 1.hour

  val dataIntegrityManagerActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[DataIntegrityManagerActor], jobsDao(), runners(), dataIntegrityInterval), "DataIntegrityManagerActor"))
}
