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
  * @param actorSystem
  */
class EventServerClient(
    host: String,
    port: Int,
    apiSecret: String,
    securedConnection: Boolean = false)(implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(
      host,
      port,
      securedConnection = securedConnection)(actorSystem)
    with LazyLogging {

  import SprayJsonSupport._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  private val SEGMENT_EVENTS = "events"
  private val SEGMENT_FILES = "files"

  val PREFIX_API_PATH = Uri.Path./ ++ Uri.Path("api") / "v1"

  val UPLOAD_URI_PATH: Uri.Path = PREFIX_API_PATH / SEGMENT_FILES
  val EVENTS_URI_PATH: Uri.Path = PREFIX_API_PATH / SEGMENT_EVENTS
  val FILES_URI_PATH: Uri.Path = PREFIX_API_PATH / SEGMENT_FILES

  val FILES_URI = toUri(FILES_URI_PATH)
  val EVENTS_URI = toUri(EVENTS_URI_PATH)
  val UPLOAD_URI = toUri(UPLOAD_URI_PATH)

  //val toUploadUrl: Uri = toUri(UPLOAD_URI_PATH)
  //val eventsUrl:Uri = toUri(EVENTS_URI_PATH)
  //val filesUrl = toApiUrl(SEGMENT_FILES)

  //val PREFIX_BASE = "/api/v1"
  //val PREFIX_EVENTS = s"$PREFIX_BASE/$SEGMENT_EVENTS"
  //val PREFIX_FILES = s"$PREFIX_BASE/$SEGMENT_FILES"

  /**
    * Create an Eve Client
    *
    * Note, the default protocol will be set to http if not explicitly provided.
    *
    * @param apiSecret   API Secret used in the auth hashing algo
    * @param actorSystem Actor System
    */
  def this(baseUrl: URL, apiSecret: String)(
      implicit actorSystem: ActorSystem) {
    this(baseUrl.getHost,
         baseUrl.getPort,
         apiSecret,
         securedConnection = (baseUrl.getProtocol == "https"))(actorSystem)
  }

  // Useful for debugging
  val logRequest: HttpRequest => HttpRequest = { r =>
    println(r.toString); r
  }
  val logResponse: HttpResponse => HttpResponse = { r =>
    println(r.toString); r
  }

  private def generateAuthHeader(method: HttpMethod,
                                 segment: Uri.Path): HttpHeader = {
    val key =
      Signer.generate(apiSecret, s"${method.value}+$segment", Signer.timestamp)
    val authHeader = s"hmac uid:$key"
    // logger.debug(s"segment '$segment' with key $key")
    RawHeader("Authentication", authHeader)
  }

  def smrtLinkSystemEventPipeline(
      method: HttpMethod,
      segment: Uri.Path): HttpRequest => Future[SmrtLinkSystemEvent] = {
    httpRequest =>
      getObject[SmrtLinkSystemEvent](
        httpRequest.withHeaders(
          immutable.Seq(generateAuthHeader(method, segment))))
  }

  def sendSmrtLinkSystemEvent(
      event: SmrtLinkSystemEvent): Future[SmrtLinkSystemEvent] =
    smrtLinkSystemEventPipeline(HttpMethods.POST, EVENTS_URI_PATH) {
      Post(EVENTS_URI, event)
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

    smrtLinkSystemEventPipeline(HttpMethods.POST, FILES_URI_PATH) {
      Post(UPLOAD_URI, createUploadEntity(pathTgz))
    }
  }

}
