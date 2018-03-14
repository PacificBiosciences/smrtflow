package com.pacbio.secondary.smrtlink.actors

import java.net.URL
import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors._
import com.pacbio.secondary.smrtlink.time.{ClockProvider, Clock}
import CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.models.{
  RegistryResourceUpdate,
  RegistryProxyRequest,
  RegistryResourceCreate,
  RegistryResource
}

import scala.language.postfixOps
import scala.collection.concurrent.TrieMap

// TODO(smcclellan): Add scaladoc

trait RegistryDao {
  def getResources(id: Option[String]): Set[RegistryResource]
  def getResource(uuid: UUID): RegistryResource
  def createResource(create: RegistryResourceCreate): RegistryResource
  def updateResource(uuid: UUID,
                     update: RegistryResourceUpdate): RegistryResource
  def deleteResource(uuid: UUID): MessageResponse
}

trait RegistryDaoProvider {
  val registryDao: Singleton[RegistryDao]
}

class InMemoryRegistryDao(clock: Clock) extends RegistryDao {
  // This is perhaps not the greatest design.
  // If either of these get out of sync with the other (e.g., raised exception)
  // there's fundamental issues
  val resources: TrieMap[UUID, RegistryResource] =
    new TrieMap[UUID, RegistryResource]

  val resourcesById: TrieMap[String, RegistryResource] =
    new TrieMap[String, RegistryResource]

  override def getResources(id: Option[String]): Set[RegistryResource] =
    id match {
      case Some(i) =>
        if (resourcesById.contains(i)) Set(resourcesById(i)) else Set.empty
      case None => resources.values.toSet
    }

  override def getResource(uuid: UUID): RegistryResource =
    if (resources contains uuid)
      resources(uuid)
    else
      throw ResourceNotFoundError(s"Unable to find resource $uuid")

  override def createResource(
      create: RegistryResourceCreate): RegistryResource =
    if (resourcesById contains create.resourceId)
      throw UnprocessableEntityError(
        s"Resource with id ${create.resourceId} already exists")
    else {
      val now = clock.dateNow()
      val resource = RegistryResource(now,
                                      UUID.randomUUID(),
                                      create.host,
                                      create.port,
                                      create.resourceId,
                                      now)
      resources += resource.uuid -> resource
      resourcesById += resource.resourceId -> resource
      resource
    }

  override def updateResource(
      uuid: UUID,
      update: RegistryResourceUpdate): RegistryResource =
    if (resources contains uuid) {
      val newResource = resources(uuid).copy(
        updatedAt = clock.dateNow(),
        host = update.host.getOrElse(resources(uuid).host),
        port = update.port.getOrElse(resources(uuid).port)
      )
      resources(uuid) = newResource
      newResource
    } else
      throw ResourceNotFoundError(s"Unable to find resource $uuid")

  override def deleteResource(uuid: UUID): MessageResponse =
    if (resources contains uuid) {
      resourcesById -= resources(uuid).resourceId
      resources -= uuid
      MessageResponse(s"Successfully deleted resource $uuid")
    } else
      throw ResourceNotFoundError(s"Unable to find resource $uuid")

  @VisibleForTesting
  def clear(): Unit = {
    resources.clear()
    resourcesById.clear()
  }
}

trait InMemoryRegistryDaoProvider extends RegistryDaoProvider {
  this: ClockProvider =>

  override val registryDao: Singleton[RegistryDao] =
    Singleton(() => new InMemoryRegistryDao(clock()))
}
