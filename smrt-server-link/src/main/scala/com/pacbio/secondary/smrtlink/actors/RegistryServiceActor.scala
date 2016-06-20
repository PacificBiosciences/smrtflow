package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import akka.actor.{Props, ActorRef, Actor}
import com.pacbio.common.actors.{PacBioActor, ActorRefFactoryProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.{RegistryResourceUpdate, RegistryProxyRequest, RegistryResourceCreate}

// TODO(smcclellan): Add scaladoc

object RegistryServiceActor {
  case class GetResources(id: Option[String])
  case class GetResource(uuid: UUID)
  case class CreateResource(create: RegistryResourceCreate)
  case class UpdateResource(uuid: UUID, update: RegistryResourceUpdate)
  case class DeleteResource(uuid: UUID)
  case class ProxyRequest(uuid: UUID, req: RegistryProxyRequest)
}

class RegistryServiceActor(registryDao: RegistryDao) extends PacBioActor {
  import RegistryServiceActor._

  def receive: Receive = {
    case GetResources(id)             => respondWith(registryDao.getResources(id))
    case GetResource(uuid)            => respondWith(registryDao.getResource(uuid))
    case CreateResource(create)       => respondWith(registryDao.createResource(create))
    case UpdateResource(uuid, update) => respondWith(registryDao.updateResource(uuid, update))
    case DeleteResource(uuid)         => respondWith(registryDao.deleteResource(uuid))
    case ProxyRequest(uuid, req)      => respondWith(registryDao.proxyRequest(uuid, req))
  }
}

trait RegistryServiceActorRefProvider {
  this: RegistryDaoProvider with ActorRefFactoryProvider =>

  val registryServiceActorRef: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[RegistryServiceActor], registryDao()), "RegistryServiceActor"))
}

trait RegistryServiceActorProvider {
  this: RegistryDaoProvider =>

  val registryServiceActor: Singleton[RegistryServiceActor] = Singleton(() => new RegistryServiceActor(registryDao()))
}