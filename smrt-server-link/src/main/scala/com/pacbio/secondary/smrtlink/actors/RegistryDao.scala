package com.pacbio.secondary.smrtlink.actors

import java.net.URL
import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.MessageResponse
import com.pacbio.common.services.PacBioServiceErrors._
import com.pacbio.common.time.{ClockProvider, Clock}
import com.pacbio.secondary.smrtlink.models.{RegistryResourceUpdate, RegistryProxyRequest, RegistryResourceCreate, RegistryResource}

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.control.NonFatal
import scalaj.http.{BaseHttp, HttpResponse, Http}

// TODO(smcclellan): Add scaladoc

trait RegistryDao {
  def getResources(id: Option[String]): Set[RegistryResource]
  def getResource(uuid: UUID): RegistryResource
  def createResource(create: RegistryResourceCreate): RegistryResource
  def updateResource(uuid: UUID, update: RegistryResourceUpdate): RegistryResource
  def deleteResource(uuid: UUID): MessageResponse
  def proxyRequest(uuid: UUID, req: RegistryProxyRequest): HttpResponse[Array[Byte]]
}

trait RegistryDaoProvider {
  val registryDao: Singleton[RegistryDao]
}

class InMemoryRegistryDao(clock: Clock, http: BaseHttp) extends RegistryDao {
  val resources: mutable.HashMap[UUID, RegistryResource] = new mutable.HashMap[UUID, RegistryResource]
  val resourcesById: mutable.HashMap[String, RegistryResource] = new mutable.HashMap[String, RegistryResource]

  override def getResources(id: Option[String]): Set[RegistryResource] =
    id match {
      case Some(i) => if (resourcesById.contains(i)) Set(resourcesById(i)) else Set.empty
      case None => resources.values.toSet
    }

  override def getResource(uuid: UUID): RegistryResource =
    if (resources contains uuid)
      resources(uuid)
    else
      throw new ResourceNotFoundError(s"Unable to find resource $uuid")

  override def createResource(create: RegistryResourceCreate): RegistryResource =
    if (resourcesById contains create.resourceId)
      throw new UnprocessableEntityError(s"Resource with id ${create.resourceId} already exists")
    else {
      val now = clock.dateNow()
      val resource = RegistryResource(now, UUID.randomUUID(), create.host, create.port, create.resourceId, now)
      resources += resource.uuid -> resource
      resourcesById += resource.resourceId -> resource
      resource
    }

  override def updateResource(uuid: UUID, update: RegistryResourceUpdate): RegistryResource =
    if (resources contains uuid) {
      val newResource = resources(uuid).copy(
        updatedAt = clock.dateNow(),
        host = update.host.getOrElse(resources(uuid).host),
        port = update.port.getOrElse(resources(uuid).port)
      )
      resources(uuid) = newResource
      newResource
    } else
      throw new ResourceNotFoundError(s"Unable to find resource $uuid")

  override def deleteResource(uuid: UUID): MessageResponse =
    if (resources contains uuid) {
      resourcesById -= resources(uuid).resourceId
      resources -= uuid
      MessageResponse(s"Successfully deleted resource $uuid")
    } else
      throw new ResourceNotFoundError(s"Unable to find resource $uuid")

  override def proxyRequest(uuid: UUID, req: RegistryProxyRequest): HttpResponse[Array[Byte]] = {
    if (resources contains uuid) {
      val url = new URL("http", resources(uuid).host, resources(uuid).port, req.path)

      var request = http(url toExternalForm)
      if (req.data isDefined) request = request.postData(req.data.get)
      if (req.params isDefined) request = request.params(req.params.get)
      if (req.headers isDefined) request = request.headers(req.headers.get)
      request = request.method(req.method)

      request.asBytes
    } else
      throw new ResourceNotFoundError(s"Unable to find resource $uuid")
  }

  @VisibleForTesting
  def clear(): Unit = {
    resources.clear()
    resourcesById.clear()
  }
}

trait InMemoryRegistryDaoProvider extends RegistryDaoProvider {
  this: ClockProvider =>

  val registryProxyHttp: Singleton[BaseHttp] = Singleton(Http)

  override val registryDao: Singleton[RegistryDao] =
    Singleton(() => new InMemoryRegistryDao(clock(), registryProxyHttp()))
}