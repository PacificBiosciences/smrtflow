package com.pacbio.secondary.smrtlink.client

import java.net.URL
import java.nio.file.Path

import spray.json._
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model._
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Multipart._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.scaladsl.{FileIO, Source}
import com.typesafe.scalalogging.LazyLogging

import com.pacbio.secondary.smrtlink.models.SmrtLinkSystemEvent
import com.pacbio.secondary.smrtlink.auth.hmac.Signer

import scala.concurrent._
import scala.concurrent.duration._
import scala.collection.immutable

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

  // Useful for debugging
  val logRequest: HttpRequest => HttpRequest = { r =>
    println(r.toString); r
  }
  val logResponse: HttpResponse => HttpResponse = { r =>
    println(r.toString); r
  }

  private def generateAuthHeader(method: HttpMethod,
                                 segment: String): HttpHeader = {
    val key =
      Signer.generate(apiSecret, s"${method.value}+$segment", Signer.timestamp)
    val authHeader = s"hmac uid:$key"
    RawHeader("Authentication", authHeader)
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
      method: HttpMethod,
      segment: String): HttpRequest => Future[SmrtLinkSystemEvent] = {
    httpRequest =>
      getObject[SmrtLinkSystemEvent](
        httpRequest.withHeaders(
          immutable.Seq(generateAuthHeader(method, segment))))
  }

  def sendSmrtLinkSystemEvent(
      event: SmrtLinkSystemEvent): Future[SmrtLinkSystemEvent] =
    smrtLinkSystemEventPipeline(HttpMethods.POST, PREFIX_EVENTS) {
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

  private def createUploadEntity(path: Path): Future[RequestEntity] = {

    // the chunk size here is currently critical for performance
    val chunkSize = 100000
    val fx = path.toFile
    val formData =
      Multipart.FormData(
        Source.single(
          Multipart.FormData.BodyPart(
            "techsupport_tgz",
            HttpEntity(MediaTypes.`application/octet-stream`,
                       fx.length(),
                       FileIO.fromPath(path, chunkSize = chunkSize)),
            Map("filename" -> fx.getName)
          )))
    Marshal(formData).to[RequestEntity]
  }

  def upload(pathTgz: Path): Future[SmrtLinkSystemEvent] = {

    smrtLinkSystemEventPipeline(HttpMethods.POST, PREFIX_FILES) {
      Post(toUploadUrl.toString, createUploadEntity(pathTgz))
    }
  }

}
