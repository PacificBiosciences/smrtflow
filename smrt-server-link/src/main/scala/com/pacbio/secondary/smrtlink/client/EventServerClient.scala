package com.pacbio.secondary.smrtlink.client

import java.net.URL
import java.nio.file.Path
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter

import spray.json._
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model._
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart._
import com.pacbio.secondary.smrtlink.models.SmrtLinkSystemEvent
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.duration._
import com.pacbio.secondary.smrtlink.auth.hmac.{
  Authentication,
  DefaultSigner,
  Directives,
  SignerConfig
}

/**
  * Create a Client for the Eve Server.
  *
  * There's some friction here with the current EventURL defined in the config, versus only defining a
  * host, port or URL, then determining the relative endpoints.
  *
  * @param baseUrl note, this is the base URL of the system, not http://my-server:8080/my-events.
  * @param actorSystem
  */
class EventServerClient(baseUrl: URL, apiSecret: String)(
    implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(baseUrl)(actorSystem)
    with DefaultSigner
    with SignerConfig
    with LazyLogging {

  import SprayJsonSupport._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  private val SEGMENT_EVENTS = "events"
  private val SEGMENT_FILES = "files"

  val PREFIX_BASE = "/api/v1"
  val PREFIX_EVENTS = s"$PREFIX_BASE/$SEGMENT_EVENTS"
  val PREFIX_FILES = s"$PREFIX_BASE/$SEGMENT_FILES"

  /**
    * Create an Eve Client
    *
    * Note, the default protocol will be set to http if not explicitly provided.
    *
    * @param host        Host name
    * @param port        Port
    * @param apiSecret   API Secret used in the auth hashing algo
    * @param actorSystem Actor System
    */
  def this(host: String, port: Int, apiSecret: String)(
      implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port), apiSecret)(actorSystem)
  }

  val sender = sendReceive

  // Useful for debugging
  val logRequest: HttpRequest => HttpRequest = { r =>
    println(r.toString); r
  }
  val logResponse: HttpResponse => HttpResponse = { r =>
    println(r.toString); r
  }

  /**
    * Add the HMAC auth key
    *
    * @param method HTTP method
    * @param segment Segment of the URL
    * @return
    */
  def sendReceiveAuthenticated(
      method: String,
      segment: String): HttpRequest => Future[HttpResponse] = {
    val key = generate(apiSecret, s"$method+$segment", timestamp)
    val authHeader = s"hmac uid:$key"
    // addHeader("Authentication", s"hmac uid:$key") ~> logRequest ~> sender ~> logResponse
    addHeader("Authentication", authHeader) ~> sender
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
    new URL(baseUrl.getProtocol,
            baseUrl.getHost,
            baseUrl.getPort,
            s"$PREFIX_BASE/$segment")
  }

  val toUploadUrl: URL = new URL(baseUrl.getProtocol,
                                 baseUrl.getHost,
                                 baseUrl.getPort,
                                 PREFIX_FILES)

  val eventsUrl = toApiUrl(SEGMENT_EVENTS)
  val filesUrl = toApiUrl(SEGMENT_FILES)

  def smrtLinkSystemEventPipeline(
      method: String,
      segment: String): HttpRequest => Future[SmrtLinkSystemEvent] =
    sendReceiveAuthenticated(method, segment) ~> unmarshal[SmrtLinkSystemEvent]

  def sendSmrtLinkSystemEvent(
      event: SmrtLinkSystemEvent): Future[SmrtLinkSystemEvent] =
    smrtLinkSystemEventPipeline("POST", PREFIX_EVENTS) {
      Post(eventsUrl.toString, event)
    }

  def sendSmrtLinkSystemEventWithBlockingRetry(
      event: SmrtLinkSystemEvent,
      numRetries: Int = 3,
      timeOutPerCall: FiniteDuration) =
    callWithBlockingRetry[SmrtLinkSystemEvent, SmrtLinkSystemEvent](
      sendSmrtLinkSystemEvent,
      event,
      numRetries,
      timeOutPerCall)

  def sendSmrtLinkSystemWithRetry(
      event: SmrtLinkSystemEvent,
      numRetries: Int = 3): Future[SmrtLinkSystemEvent] = {
    callWithRetry[SmrtLinkSystemEvent, SmrtLinkSystemEvent](
      sendSmrtLinkSystemEvent,
      event,
      numRetries)
  }

  def upload(pathTgz: Path): Future[SmrtLinkSystemEvent] = {
    val multiForm = Multipart.FormData(
      Seq(
        BodyPart(pathTgz.toFile,
                 "techsupport_tgz",
                 ContentType(MediaTypes.`application/octet-stream`)))
    )

    smrtLinkSystemEventPipeline("POST", PREFIX_FILES) {
      Post(toUploadUrl.toString, multiForm)
    }
  }

}
