// derived from PrimaryClient.scala in PAWS
package com.pacbio.secondary.smrttools.client

import java.net.URL

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future

object ClientModels {
  // the xfer service isn't upto date with all the fields
  case class ServiceStatus(id: String, message: String)
}

// XXX should this use PacBioJsonProtocol instead?
trait ServicesClientJsonProtocol extends DefaultJsonProtocol {
  import ClientModels._
 
  implicit val serviceStatusFormat = jsonFormat2(ServiceStatus)
}

object ServicesClientJsonProtocol extends ServicesClientJsonProtocol

/**
 * Client to Primary Services
 */
class ServiceAccessLayer(val baseUrl: URL)(implicit actorSystem: ActorSystem) {

  import ServicesClientJsonProtocol._
  import SprayJsonSupport._
  import ClientModels._

  // Context to run futures in
  implicit val executionContext = actorSystem.dispatcher

  private def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString

  // Pipelines and serialization
  def respPipeline: HttpRequest => Future[HttpResponse] = sendReceive
  def serviceStatusPipeline: HttpRequest => Future[ServiceStatus] = sendReceive ~> unmarshal[ServiceStatus]

  val statusUrl = toUrl("/status")

  def getStatus: Future[ServiceStatus] = serviceStatusPipeline {
    Get(statusUrl)
  }
}
