package com.pacbio.common.actors

import akka.actor.{ActorRef, Props}
import akka.pattern.pipe
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.ConfigCleanupJobCreate

import scala.concurrent.ExecutionContext.Implicits._

// TODO(smcclellan): add scaladoc, unittests

object CleanupServiceActor {
  case class CreateConfigJob(create: ConfigCleanupJobCreate)
  case class RunConfigJob(name: String)
}

class CleanupServiceActor(dao: CleanupDao) extends PacBioActor {
  import CleanupServiceActor._

  def receive: Receive = {
    case CreateConfigJob(create) => dao.createConfigJob(create) pipeTo sender
    case RunConfigJob(name)      => dao.runConfigJob(name)      pipeTo sender
  }
}

trait CleanupServiceActorRefProvider {
  this: CleanupDaoProvider with ActorRefFactoryProvider =>

  final val cleanupServiceActorRef: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[CleanupServiceActor], cleanupDao())))
}

trait CleanupServiceActorProvider {
  this: CleanupDaoProvider =>

  final val logServiceActor: Singleton[CleanupServiceActor] = Singleton(() => new CleanupServiceActor(cleanupDao()))
}