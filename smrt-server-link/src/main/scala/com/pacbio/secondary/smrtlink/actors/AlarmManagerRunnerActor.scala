package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorRef, Props}

import scala.concurrent.ExecutionContext.Implicits.global
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.file.FileSystemUtilProvider
import com.pacbio.secondary.smrtlink.models.{AlarmSeverity, AlarmStatus}
import CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.AlarmDaoActor.UpdateAlarmStatus
import com.pacbio.secondary.smrtlink.actors.AlarmManagerRunnerActor.{
  RunAlarmById,
  RunAlarms
}
import com.pacbio.secondary.smrtlink.alarms.{AlarmRunner, _}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success}
import concurrent.duration._

object AlarmManagerRunnerActor {
  case object RunAlarms
  case class RunAlarmById(id: String)
}

class AlarmManagerRunnerActor(runners: Seq[AlarmRunner], daoActor: ActorRef)
    extends Actor
    with LazyLogging {

  context.system.scheduler.scheduleOnce(5.seconds, self, RunAlarms)

  override def preStart() = {
    logger.info(
      s"Starting $self with ${runners.length} Alarm Runners. Runner Ids:${runners
        .map(_.alarm.id)}")
  }

  /**
    * Run Alarm and send the update status back to the DAO. This is a fire and forget model
    * @param runner Alarm Runner
    */
  def runAlarm(runner: AlarmRunner): Unit = {
    logger.info(s"Running Alarm ${runner.alarm.id} $runner")
    runner.run() onComplete {
      case Success(alarmStatus) =>
        daoActor ! UpdateAlarmStatus(runner.alarm.id, alarmStatus)
      case Failure(ex) =>
        daoActor ! UpdateAlarmStatus(runner.alarm.id,
                                     AlarmStatus(runner.alarm.id,
                                                 1.0,
                                                 Some(ex.getMessage),
                                                 AlarmSeverity.ERROR,
                                                 JodaDateTime.now()))
    }
  }

  override def receive: Receive = {
    case RunAlarms =>
      logger.debug(s"Running All (${runners.length}) Alarm Runners")
      // Returns MessageResponse
      runners.foreach(runAlarm)
      sender ! MessageResponse(
        s"Triggered running ${runners.length} Alarm Runners with ids ${runners
          .map(_.alarm.id)
          .reduce(_ + "," + _)}")
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
    with AlarmDaoActorProvider
    with FileSystemUtilProvider
    with AlarmRunnerLoaderProvider =>

  val alarmManagerRunnerActor: Singleton[ActorRef] = Singleton { () =>
    actorRefFactory().actorOf(
      Props(classOf[AlarmManagerRunnerActor], alarmRunners(), alarmDaoActor()))
  }
}
