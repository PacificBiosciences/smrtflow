package com.pacbio.common.client

import com.pacbio.common.models.{PacBioJsonProtocol, ServiceStatus}

import akka.actor.ActorSystem
import spray.client.pipelining._
import scala.concurrent.duration._

import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

import java.net.URL

import scala.language.postfixOps


class ServiceAccessLayer(val baseUrl: URL)(implicit actorSystem: ActorSystem) {

  import PacBioJsonProtocol._
  import SprayJsonSupport._

  implicit val executionContext = actorSystem.dispatcher

  protected def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString
  protected def toUiRootUrl(port: Int): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, port, "/").toString

  // Override this in subclasses
  def serviceStatusEndpoints: Vector[String] = Vector()

  // Pipelines and serialization
  def respPipeline: HttpRequest => Future[HttpResponse] = sendReceive
  def rawJsonPipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
  def serviceStatusPipeline: HttpRequest => Future[ServiceStatus] = sendReceive ~> unmarshal[ServiceStatus]

  val statusUrl = toUrl("/status")

  def getStatus: Future[ServiceStatus] = serviceStatusPipeline {
    Get(statusUrl)
  }

  def getEndpoint(endpointUrl: String): Future[HttpResponse] = respPipeline {
    Get(endpointUrl)
  }

  def getServiceEndpoint(endpointPath: String): Future[HttpResponse] = respPipeline {
    Get(toUrl(endpointPath))
  }

  def checkEndpoint(endpointUrl: String): Int = {
    Try {
      Await.result(getEndpoint(endpointUrl), 20 seconds)
    } match {
      // FIXME need to make this more generic
      case Success(x) => {
        x.status match {
          case StatusCodes.Success(_) =>
            println(s"found endpoint ${endpointUrl}")
            0
          case _ =>
            println(s"error retrieving ${endpointUrl}: ${x.status}")
            1
        }
      }
      case Failure(err) => {
        println(s"failed to retrieve endpoint ${endpointUrl}")
        println(s"${err}")
        1
      }
    }
  }

  def checkServiceEndpoint(endpointPath: String): Int = checkEndpoint(toUrl(endpointPath))

  def checkUiEndpoint(uiPort: Int): Int = checkEndpoint(toUiRootUrl(uiPort))

  def checkServiceEndpoints: Int = {
    var xc = 0
    for (endpointPath <- serviceStatusEndpoints) {
      val epStatus = checkServiceEndpoint(endpointPath)
      if (epStatus > 0) xc = epStatus
    }
    xc
  }

}
