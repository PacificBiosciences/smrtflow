package com.pacbio.secondary.smrtlink.client

import java.net.URL

import spray.json._
import spray.client.pipelining._
import spray.http.HttpRequest
import spray.http._
import spray.httpx.SprayJsonSupport
import akka.actor.ActorSystem
import com.pacbio.common.client.{ServiceAccessLayer, UrlUtils}
import com.pacbio.secondary.smrtlink.models.{SmrtLinkJsonProtocols, SmrtLinkSystemEvent}

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

/**
  * Create a Client for the Event Server.
  *
  * There's some friction here with the current EventURL defined in the config, versus only defining a
  * host, port or URL, then determining the relative endpoints.
  *
  * @param baseUrl note, this is the base URL of the system, not http://my-server:8080/my-events.
  * @param actorSystem
  */
class EventServerClient(baseUrl: URL)(implicit actorSystem: ActorSystem) extends ServiceAccessLayer(baseUrl)(actorSystem) {

  import SprayJsonSupport._
  import SmrtLinkJsonProtocols._

  val BASE_PREFIX = "api/v1"
  private val EVENTS_SEGMENT = "events"

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }

  /**
    * Create URL relative to the base prefix segment
    *
    * wtf does super.toUrl return a String?
    *
    * @param segment relative segment to the base '/api/vi/' prefix
    * @return
    */
  def toApiUrl(segment: String): URL = {
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, s"$BASE_PREFIX/$EVENTS_SEGMENT")
  }

  // Base Events url
  val eventsUrl = toApiUrl(EVENTS_SEGMENT)

  def smrtLinkSystemEventPipeline: HttpRequest => Future[SmrtLinkSystemEvent] =
    sendReceive ~> unmarshal[SmrtLinkSystemEvent]

  def sendSmrtLinkSystemEvent(event: SmrtLinkSystemEvent): Future[SmrtLinkSystemEvent] =
    smrtLinkSystemEventPipeline { Get(eventsUrl.toString)}

  def sendSmrtLinkSystemEventWithBlockingRetry(event: SmrtLinkSystemEvent, numRetries: Int = 3, timeOutPerCall: FiniteDuration) =
    callWithBlockingRetry[SmrtLinkSystemEvent, SmrtLinkSystemEvent](sendSmrtLinkSystemEvent, event, numRetries, timeOutPerCall)

  def sendSmrtLinkSystemWithRetry(event: SmrtLinkSystemEvent, numRetries: Int = 3): Future[SmrtLinkSystemEvent] = {
    callWithRetry[SmrtLinkSystemEvent, SmrtLinkSystemEvent](sendSmrtLinkSystemEvent, event, numRetries)
  }

}
