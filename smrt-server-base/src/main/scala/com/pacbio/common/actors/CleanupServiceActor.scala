package com.pacbio.common.actors

import akka.actor.{Props, ActorRef}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.ConfigCleanupJobCreate

// TODO(smcclellan): add scaladoc, unittests

object CleanupServiceActor {
  case class CreateConfigJob(create: ConfigCleanupJobCreate)
  case class RunConfigJob(name: String)
}

class CleanupServiceActor(dao: CleanupDao) extends PacBioActor {
  import CleanupServiceActor._

  def receive: Receive = {
    case CreateConfigJob(create) => respondWith(dao.createConfigJob(create))
    case RunConfigJob(name)      => respondWith(dao.runConfigJob(name))
  }
}

trait CleanupServiceActorRefProvider {
  this: CleanupDaoProvider with ActorRefFactoryProvider =>

  final val cleanupServiceActorRef: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[CleanupServiceActor], cleanupDao()), "CleanupServiceActor"))
}

trait CleanupServiceActorProvider {
  this: CleanupDaoProvider =>

  final val logServiceActor: Singleton[CleanupServiceActor] = Singleton(() => new CleanupServiceActor(cleanupDao()))
}