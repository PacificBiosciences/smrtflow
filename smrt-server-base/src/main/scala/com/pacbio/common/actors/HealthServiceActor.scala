package com.pacbio.common.actors

import akka.actor.{Props, ActorRef, Actor}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{HealthGaugeMessageRecord, HealthGaugeRecord}

/**
 * Companion object for the HealthServiceActor class, defining the set of messages it can handle.
 */
object HealthServiceActor {
  case object GetAllGauges
  case class GetGauge(id: String)
  case class CreateGauge(m: HealthGaugeRecord)
  case class GetAllMessages(id: String)
  case class CreateMessage(id: String, m: HealthGaugeMessageRecord)
  case object GetSevereGauges
}

/**
 * Akka actor that wraps a HealthDao.
 */
class HealthServiceActor(healthDao: HealthDao) extends PacBioActor {

  import HealthServiceActor._

  def receive: Receive = {
    case GetAllGauges                                           => respondWith(healthDao.getAllHealthGauges)
    case GetGauge(id: String)                                   => respondWith(healthDao.getHealthGauge(id))
    case CreateGauge(m: HealthGaugeRecord)                      => respondWith(healthDao.createHealthGauge(m))
    case GetAllMessages(id: String)                             => pipeWith(healthDao.getAllHealthMessages(id))
    case CreateMessage(id: String, m: HealthGaugeMessageRecord) => respondWith(healthDao.createHealthMessage(id, m))
    case GetSevereGauges                                        => respondWith(healthDao.getSevereHealthGauges)
  }
}

/**
 * Provides a singleton ActorRef for a HealthServiceActor. Concrete providers must mixin a HealthDaoProvider and an
 * ActorRefFactoryProvider.
 */
trait HealthServiceActorRefProvider {
  this: HealthDaoProvider with ActorRefFactoryProvider =>

  final val healthServiceActorRef: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[HealthServiceActor], healthDao())))
}

/**
 * Provides a singleton HealthServiceActor. Concrete providers must mixin a HealthDaoProvider. Note that this provider
 * is designed for tests, and should generally not be used in production. To create a production app, use the
 * {{{HealthServiceActorRefProvider}}}.
 */
trait HealthServiceActorProvider {
  this: HealthDaoProvider =>

  final val healthServiceActor: Singleton[HealthServiceActor] = Singleton(() => new HealthServiceActor(healthDao()))
}