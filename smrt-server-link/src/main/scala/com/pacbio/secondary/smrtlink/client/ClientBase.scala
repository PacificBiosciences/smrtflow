package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.models.ServiceStatus
import spray.client.pipelining._
import spray.http.HttpRequest
import spray.httpx.SprayJsonSupport

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Base Client trait for
  *
  */
trait ClientBase extends Retrying{

  // This starts to tangle up specific JSON conversion with the Client
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import SprayJsonSupport._

  implicit val actorSystem: ActorSystem
  val baseUrl: URL

  // This should really return a URL instance, not a string
  def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, baseUrl.getPath + segment).toString

  protected def serviceStatusPipeline: HttpRequest => Future[ServiceStatus] = sendReceive ~> unmarshal[ServiceStatus]

  val statusUrl = toUrl("/status")

  /**
    * Get Status of the System. The model must adhere to the SmrtServer Status
    * message schema.
    *
    * @return
    */
  def getStatus: Future[ServiceStatus] = serviceStatusPipeline {
    Get(statusUrl)
  }
  def getStatusWithRetry(maxRetries: Int = 3, retryDelay: FiniteDuration = 1.second): Future[ServiceStatus] =
    retry[ServiceStatus](getStatus, retryDelay, maxRetries)(actorSystem.dispatcher, actorSystem.scheduler)

}
