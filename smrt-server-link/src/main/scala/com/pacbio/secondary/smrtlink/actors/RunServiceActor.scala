package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import akka.actor.{Props, ActorRef}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models._

import scala.concurrent.ExecutionContext.Implicits._

/**
 * Represents a set of search criteria for searching run designs.
 * @param name if present, only run designs whose name matches the given name will be returned
 * @param substring if present, only run designs whose name or summary contains the substring will be returned
 * @param createdBy if present, only run designs created by the given user will be returned
 * @param reserved if present, only run designs that are in the given reserve state will be returned
 */
case class SearchCriteria(
    name: Option[String],
    substring: Option[String],
    createdBy: Option[String],
    reserved: Option[Boolean])

/**
 * Companion object for the RunServiceActor class, defining the set of messages it can handle.
 */
object RunServiceActor {
  case class GetRuns(criteria: SearchCriteria)
  case class GetRun(id: UUID)
  case class CreateRun(create: RunCreate)
  case class UpdateRun(id: UUID, update: RunUpdate)
  case class DeleteRun(id: UUID)
  case class GetCollections(runId: UUID)
  case class GetCollection(runId: UUID, id: UUID)
}

/**
 * Akka actor that wraps a RunDao.
 */
class RunServiceActor(runDao: RunDao) extends PacBioActor {

  import RunServiceActor._

  def receive: Receive = {
    case GetRuns(criteria)        => pipeWith(runDao.getRuns(criteria))
    case GetRun(id)               => pipeWith(runDao.getRun(id))
    case CreateRun(create)        => pipeWith(runDao.createRun(create))
    case UpdateRun(id, update)    => pipeWith(runDao.updateRun(id, update))
    case DeleteRun(id)            => pipeWith(runDao.deleteRun(id))
    case GetCollections(runId)    => pipeWith(runDao.getCollectionMetadatas(runId))
    case GetCollection(runId, id) => pipeWith(runDao.getCollectionMetadata(runId, id))
  }
}


/**
 * Provides a singleton ActorRef for a RunServiceActor. Concrete providers must mixin a RunDaoProvider and
 * an ActorRefFactoryProvider.
 */
trait RunServiceActorRefProvider {
  this: RunDaoProvider with ActorRefFactoryProvider =>

  final val runServiceActorRef: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[RunServiceActor], runDao()), "RunServiceActor"))
}

/**
 * Provides a singleton RunServiceActor. Concrete providers must mixin a RunDaoProvider. Note that this
 * provider is designed for tests, and should generally not be used in production. To create a production app, use the
 * {{{RunServiceActorRefProvider}}}.
 */
trait RunServiceActorProvider {
  this: RunDaoProvider =>

  final lazy val runServiceActor: Singleton[RunServiceActor] =
    Singleton(() => new RunServiceActor(runDao()))
}
