package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._

import scala.concurrent.ExecutionContext.Implicits.global
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.file.FileSystemUtilProvider
import com.pacbio.secondary.smrtlink.models._
import CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.AlarmDaoActor.{
  GetAllAlarmStatus,
  UpdateAlarmStatus
}
import com.pacbio.secondary.smrtlink.actors.AlarmManagerRunnerActor.{
  RunAlarmById,
  RunAlarms
}
import com.pacbio.secondary.smrtlink.actors.EventManagerActor.CreateEvent
import com.pacbio.secondary.smrtlink.alarms.{AlarmRunner, _}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success}
import concurrent.duration._
import concurrent.Future
import scala.util.control.NonFatal

import spray.json._

object AlarmManagerRunnerActor {
  case object RunAlarms
  case class RunAlarmById(id: String)
}

class AlarmManagerRunnerActor(runners: Seq[AlarmRunner],
                              daoActor: ActorRef,
                              eventManagerActor: ActorRef,
                              enableInternalMetrics: Boolean)
    extends Actor
    with LazyLogging {

  val DEFAULT_ACTOR_TIMEOUT: FiniteDuration = 10.seconds
  // This is used in any ask calls to other actors
  implicit val daoUpdateTimeout = akka.util.Timeout(DEFAULT_ACTOR_TIMEOUT)

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  context.system.scheduler.scheduleOnce(5.seconds, self, RunAlarms)

  override def preStart() = {
    logger.info(
      s"Starting $self with ${runners.length} Alarm Runners. Runner Ids:${runners
        .map(_.alarm.id)} with enableInternalMetrics=$enableInternalMetrics")
  }

  /**
    * Run Alarm and send the update status back to the DAO. This is a fire and forget model
    * @param runner Alarm Runner
    */
  def runAlarm(runner: AlarmRunner): Future[Option[AlarmStatus]] = {
    logger.info(s"Running Alarm ${runner.alarm.id} $runner")

    val fx = for {
      alarmStatus <- runner.run()
      optStatus <- (daoActor ? UpdateAlarmStatus(runner.alarm.id, alarmStatus))
        .mapTo[Option[AlarmStatus]]
    } yield optStatus

    val gx: Future[Option[AlarmStatus]] = fx.recoverWith {
      case NonFatal(ex) =>
        (daoActor ? UpdateAlarmStatus(runner.alarm.id,
                                      AlarmStatus(runner.alarm.id,
                                                  1.0,
                                                  Some(ex.getMessage),
                                                  AlarmSeverity.ERROR,
                                                  JodaDateTime.now())))
          .mapTo[Option[AlarmStatus]]
    }

    gx
  }

  private def convertToSmrtLinkEvent(alarms: Seq[AlarmStatus]): SmrtLinkEvent = {
    val m = InternalAlarms(alarms)
    SmrtLinkEvent.from(EventTypes.ALARMS, m.toJson.asJsObject)
  }

  private def getAndUpdateAlarms(): Future[SmrtLinkEvent] = {
    for {
      alarmsStatus <- (daoActor ? GetAllAlarmStatus).mapTo[Seq[AlarmStatus]]
      event <- Future.successful(convertToSmrtLinkEvent(alarmsStatus))
    } yield event
  }

  private def getAlarmsAndSendEvent(): Future[MessageResponse] = {

    val fx = getAndUpdateAlarms()

    fx.foreach { event =>
      eventManagerActor ! CreateEvent(event)
    }

    fx.map(
      event =>
        MessageResponse(
          s"Sent message ${event.eventTypeId} ${event.uuid} to EventManager"))
  }

  private def sendInternalAlarmsIfConfigured(): Future[MessageResponse] = {
    if (enableInternalMetrics) {
      getAlarmsAndSendEvent()
    } else {
      Future.successful(MessageResponse(
        "System is not configured for internal metrics. Not sending Alarms."))
    }
  }

  override def receive: Receive = {
    case RunAlarms =>
      logger.debug(s"Running All (${runners.length}) Alarm Runners")
      // Returns MessageResponse
      sender ! MessageResponse(
        s"Triggered running ${runners.length} Alarm Runners with ids ${runners
          .map(_.alarm.id)
          .reduce(_ + "," + _)}")

      val fx = for {
        _ <- Future.traverse(runners)(runAlarm)
        msg <- sendInternalAlarmsIfConfigured()
      } yield msg

      fx onComplete {
        case Success(msg) =>
          logger.debug(msg.message)
        case Failure(ex) =>
          logger.error(s"Failed to update alarms ${ex.getMessage}")
      }

    case RunAlarmById(id) =>
      // Returns Some(MessageResponse) if the runner is found
      runners.find(_.alarm.id == id) match {
        case Some(runner) =>
          runAlarm(runner)
          sender ! Some(
            MessageResponse(s"Triggered Alarm Runner ${runner.alarm.id}"))
        case _ =>
          logger.error(s"Unable to find Alarm Runner '$id'")
          sender ! None
      }

  }
}

/**
  * Supporting Optional config driven loading of Alarms doesn't compose in the singleton provider model.
  *
  * Encapsulating ALL alarm runner loading into this location.
  *
  */
trait AlarmRunnerLoaderProvider {
  this: SmrtLinkConfigProvider
    with ActorSystemProvider
    with FileSystemUtilProvider =>

  val alarmRunners: Singleton[Seq[AlarmRunner]] = Singleton { () =>
    implicit val system = actorSystem()

    val tmpDirDiskSpaceAlarmRunner =
      new TmpDirectoryAlarmRunner(smrtLinkTempDir(), fileSystemUtil())
    val jobDirDiskSpaceAlarmRunner =
      new JobDirectoryAlarmRunner(jobEngineConfig().pbRootJobDir,
                                  fileSystemUtil())

    val chemistryAlarmRunner = externalBundleUrl().map(url =>
      new ExternalChemistryServerAlarmRunner(url))
    val eveAlarmRunner = externalEveUrl().map(url =>
      new ExternalEveServerAlarmRunner(url, apiSecret()))

    Seq(Some(tmpDirDiskSpaceAlarmRunner),
        Some(jobDirDiskSpaceAlarmRunner),
        chemistryAlarmRunner,
        eveAlarmRunner).flatten
  }

}

trait AlarmManagerRunnerProvider {
  this: ActorRefFactoryProvider
    with SmrtLinkConfigProvider
    with AlarmDaoActorProvider
    with FileSystemUtilProvider
    with AlarmRunnerLoaderProvider
    with EventManagerActorProvider =>

  val alarmManagerRunnerActor: Singleton[ActorRef] = Singleton { () =>
    actorRefFactory().actorOf(
      Props(classOf[AlarmManagerRunnerActor],
            alarmRunners(),
            alarmDaoActor(),
            eventManagerActor(),
            enableInternalMetrics()))
  }
}
