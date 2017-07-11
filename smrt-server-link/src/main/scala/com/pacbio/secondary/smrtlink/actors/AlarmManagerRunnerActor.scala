package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorRef, Props}
import scala.concurrent.ExecutionContext.Implicits.global

import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.alarms.AlarmRunner
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{AlarmSeverity, AlarmStatus}
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.AlarmDaoActor.UpdateAlarmStatus
import com.pacbio.secondary.smrtlink.actors.AlarmManagerRunnerActor.{RunAlarmById, RunAlarms}
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success}

object AlarmManagerRunnerActor {
  case object RunAlarms
  case class RunAlarmById(id: String)
}

class AlarmManagerRunnerActor(runners: Seq[AlarmRunner], daoActor: ActorRef) extends Actor with LazyLogging{

  /**
    * Run Alarm and send the update status back to the DAO. This is a fire and forget model
    * @param runner Alarm Runner
    */
  def runAlarm(runner: AlarmRunner): Unit = {
    runner.run() onComplete {
      case Success(alarmStatus) => daoActor ! UpdateAlarmStatus(runner.alarm.id, alarmStatus)
      case Failure(ex) => daoActor ! UpdateAlarmStatus(runner.alarm.id, AlarmStatus(runner.alarm.id, 1.0, Some(ex.getMessage), AlarmSeverity.ERROR))
    }
  }

  override def receive: Receive = {
    case RunAlarms =>
      // Returns MessageResponse
      runners.foreach(runAlarm)
      sender ! MessageResponse(s"Triggered running ${runners.length} Alarm Runners with ids ${runners.map(_.alarm.id).reduce(_ + "," + _)}")
    case RunAlarmById(id) =>
      // Returns Some(MessageResponse) if the runner is found
      runners.find(_.alarm.id == id) match {
        case Some(runner) =>
          runAlarm(runner)
          sender ! Some(MessageResponse(s"Triggered Alarm Runner ${runner.alarm.id}"))
        case _ =>
          logger.error(s"Unable to find Alarm Runner '$id'")
          sender ! None
      }

  }
}

trait AlarmManagerRunnerProvider {
  this: ActorRefFactoryProvider with AlarmDaoActorProvider =>

  //FIXME. This needs to be loaded from the SMRT Link System Config
  val alarmRunners: Singleton[Seq[AlarmRunner]] = Singleton(() => Seq.empty[AlarmRunner])

  val alarmManagerRunnerActor: Singleton[ActorRef] = Singleton {
    () => actorRefFactory().actorOf(Props(classOf[AlarmManagerRunnerActor], alarmRunners(), alarmDaoActor()))
  }
}
