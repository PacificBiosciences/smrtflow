package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import akka.actor.{ActorRef, Props}
import com.pacbio.common.actors.{ActorRefFactoryProvider, PacBioActor}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.{SampleCreate, SampleUpdate}

import scala.concurrent.ExecutionContext.Implicits._

case object SampleServiceActor {
  case class GetSamples()
  case class GetSample(uniqueId: UUID)
  case class CreateSample(login: String, create: SampleCreate)
  case class UpdateSample(uniqueId: UUID, update: SampleUpdate)
  case class DeleteSample(uniqueId: UUID)
}

class SampleServiceActor(sampleDao: SampleDao) extends PacBioActor {

  import SampleServiceActor._

  def receive: Receive = {
    case GetSamples()                   => pipeWith(sampleDao.getSamples())
    case GetSample(uniqueId)            => pipeWith(sampleDao.getSample(uniqueId))
    case CreateSample(login, create)    => pipeWith(sampleDao.createSample(login, create))
    case UpdateSample(uniqueId, update) => pipeWith(sampleDao.updateSample(uniqueId, update))
    case DeleteSample(uniqueId)         => pipeWith(sampleDao.deleteSample(uniqueId))
  }
}

/**
 * Provides a singleton ActorRef for a SampleServiceActor. Concrete providers must mixin a SampleDaoProvider and
 * an ActorRefFactoryProvider.
 */
trait SampleServiceActorRefProvider {
  this: SampleDaoProvider with ActorRefFactoryProvider =>

  val sampleServiceActorRef: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[SampleServiceActor], sampleDao()), "SampleServiceActor"))
}

/**
 * Provides a singleton SampleServiceActor. Concrete providers must mixin a SampleDaoProvider. Note that this
 * provider is designed for tests, and should generally not be used in production. To create a production app, use the
 * {{{SampleServiceActorRefProvider}}}.
 */
trait SampleServiceActorProvider {
  this: SampleDaoProvider =>

  val sampleServiceActor: Singleton[SampleServiceActor] =
    Singleton(() => new SampleServiceActor(sampleDao()))
}