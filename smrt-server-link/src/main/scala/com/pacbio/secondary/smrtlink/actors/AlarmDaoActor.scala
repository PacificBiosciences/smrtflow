package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Actor, ActorRef, Props}
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.AlarmStatus
import com.typesafe.scalalogging.LazyLogging

import collection.mutable


object AlarmDaoActor {
  // Message Protocols
  case object GetAllAlarmStatus
  case class GetAlarmStatusById(id: String)
  case class UpdateAlarmStatus(id: String, status:AlarmStatus)
}

class AlarmDaoActor(status:mutable.Map[String, AlarmStatus]) extends Actor with LazyLogging{

  import AlarmDaoActor._


  override def receive: Receive = {
    case GetAllAlarmStatus =>
      sender ! status.values.toSeq
    case GetAlarmStatusById(id) =>
      // Return Option[AlarmStatus]
      sender ! status.get(id)
    case UpdateAlarmStatus(id, alarmStatus) =>
      // Return Option[AlarmStatus]
      status += (id -> alarmStatus)
      sender ! status.get(id)
    case x =>
      logger.warn(s"Unhandled message $x to Actor $self")
  }

}

trait AlarmDaoActorProvider {
  this: ActorRefFactoryProvider =>

  val alarmDaoActor: Singleton[ActorRef] = Singleton(() => actorRefFactory().actorOf(Props(classOf[AlarmDaoActor], mutable.Map.empty[String, AlarmStatus])))
}
