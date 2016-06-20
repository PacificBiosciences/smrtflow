package com.pacbio.common.actors

import akka.actor.{Props, ActorRef}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{ApiCleanupJobCreate, ConfigCleanupJobCreate}

// TODO(smcclellan): add scaladoc, unittests

object CleanupServiceActor {
  case object GetAllJobs
  case class GetJob(id: String)
  case class CreateJob(create: ApiCleanupJobCreate)
  case class StartJob(id: String)
  case class PauseJob(id: String)
  case class DeleteJob(id: String)

  case class CreateConfigJob(create: ConfigCleanupJobCreate)
  case class RunConfigJob(name: String)
}

class CleanupServiceActor(dao: CleanupDao) extends PacBioActor {
  import CleanupServiceActor._

  def receive: Receive = {
    case GetAllJobs        => respondWith(dao.getAllJobs())
    case GetJob(id)        => respondWith(dao.getJob(id))
    case CreateJob(create) => respondWith(dao.createJob(create))
    case StartJob(id)      => respondWith(dao.startJob(id))
    case PauseJob(id)      => respondWith(dao.pauseJob(id))
    case DeleteJob(id)     => respondWith(dao.deleteJob(id))

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