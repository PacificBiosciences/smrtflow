package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.headers.{HttpEncodings, `Accept-Encoding`}
import akka.pattern.after
import akka.stream.ActorMaterializer

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
//import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import com.pacbio.secondary.smrtlink.models.ServiceStatus

// Move this to a central location
trait UrlUtils {
  def convertToUrl(host: String, port: Int, protocol: String = "http") = {
    val h = host.replaceFirst(s"$protocol://", "")
    new URL(s"$protocol://$h:$port")
  }
}

object UrlUtils extends UrlUtils

// Lifted from https://gist.github.com/viktorklang/9414163
trait Retrying {
  def retry[T](f: => Future[T], delay: FiniteDuration, retries: Int)(
      implicit ec: ExecutionContext,
      s: Scheduler): Future[T] = {
    f recoverWith {
      case _ if retries > 0 => after(delay, s)(retry(f, delay, retries - 1))
    }
  }
}

/**
  * Base Client trait
  *
  */
trait ClientBase extends Retrying {

  // This starts to tangle up specific JSON conversion with the Client
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import SprayJsonSupport._

  implicit val actorSystem: ActorSystem
  implicit val materializer = ActorMaterializer()
  implicit val ec = actorSystem.dispatcher

  lazy val http = Http()

  def RootUri: Uri

  def toUri(path: Uri.Path): Uri = RootUri.copy(path = Uri.Path / path)

  lazy val statusUrl: Uri = toUri(Uri.Path("status"))

  protected def sendRequest(request: HttpRequest): Future[HttpResponse] =
    http.singleRequest(request)

  protected def getEndpoint(endpointUrl: Uri): Future[HttpResponse] =
    sendRequest(Get(endpointUrl))

  private def decodeResponse(response: HttpResponse): HttpResponse = {
    val decoder = response.encoding match {
      case HttpEncodings.gzip ⇒
        Gzip
      case HttpEncodings.deflate ⇒
        Deflate
      case HttpEncodings.identity ⇒
        NoCoding
      case _ =>
        // This perhaps should raise?
        NoCoding
    }

    decoder.decodeMessage(response)
  }

  // Useful for debugging
  private val customLogRequest: HttpRequest => HttpRequest = { r =>
    println(r.toString)
    r
  }

  private val customLogResponse: HttpResponse => HttpResponse = { r =>
    //println(r.toString)
    println(s"Status         ${r.status}")
    println(s"Headers        ${r.headers}")
    println(s"Content Length ${r.entity.contentLengthOption}")
    r
  }

  protected def getResponse(request: HttpRequest): Future[HttpResponse] = {

    val req = request.addHeader(
      `Accept-Encoding`(HttpEncodings.gzip, HttpEncodings.deflate))

    sendRequest(req).flatMap { response =>
      response.status match {
        case StatusCodes.OK | StatusCodes.Created =>
          // customLogResponse(response)
          Future.successful(response)
        case _ =>
          Future.failed(
            new Exception(
              s"HTTP ERROR ${response.status}: ${response.toString}"))
      }
    }
  }

  protected def getObject[T](request: HttpRequest)(
      implicit um: Unmarshaller[HttpResponse, T]): Future[T] =
    getResponse(request).map(decodeResponse).flatMap(um(_))

  /**
    * Get Status of the System. The model must adhere to the SmrtServer Status
    * message schema.
    *
    * @return
    */
  def getStatus: Future[ServiceStatus] =
    getObject[ServiceStatus](Get(statusUrl))

  def getStatusWithRetry(
      maxRetries: Int = 3,
      retryDelay: FiniteDuration = 1.second): Future[ServiceStatus] =
    retry[ServiceStatus](getStatus, retryDelay, maxRetries)(
      actorSystem.dispatcher,
      actorSystem.scheduler)

  // This should have a backoff model to wait a few seconds before the retry. It should
  // also have a better error message that includes the total number of retries
  protected def callWithRetry[A, B](f: (A => Future[B]),
                                    input: A,
                                    numRetries: Int): Future[B] = {
    f(input).recoverWith {
      case NonFatal(_) if numRetries > 0 =>
        callWithRetry[A, B](f, input, numRetries - 1)
    }
  }

  protected def callWithBlockingRetry[A, B](
      f: (A => Future[B]),
      input: A,
      numRetries: Int = 3,
      timeOutPerCall: FiniteDuration): Try[B] = {
    Try {
      Await.result[B](f(input), timeOutPerCall)
    } match {
      case Success(r) => Success(r)
      case Failure(ex) =>
        if (numRetries > 0)
          callWithBlockingRetry[A, B](f, input, numRetries - 1, timeOutPerCall)
        else Failure(ex)
    }
  }

  /**
    * Checks an relative Endpoint for Success (HTTP 200) and returns non-zero
    * exit code on failure.
    *
    * Note, this is blocking.
    *
    * @param endpointUrl Relative endpoint segment
    * @param timeOut     Max timeout for status request
    * @return
    */
  def checkEndpoint(endpointUrl: Uri,
                    timeOut: FiniteDuration = 20.seconds): Int = {

    def statusToInt(status: StatusCode): Int = {
      status match {
        case StatusCodes.Success(_) =>
          println(s"found endpoint $endpointUrl")
          0
        case _ =>
          println(s"error retrieving $endpointUrl: $status")
          1
      }
    }

    Try {
      Await.result(getEndpoint(endpointUrl), timeOut)
    } match {
      // FIXME need to make this more generic
      case Success(x) => statusToInt(x.status)
      case Failure(err) => {
        println(s"failed to retrieve endpoint $endpointUrl")
        println(s"$err")
        1
      }
    }
  }
}

/**
  * Base class for clients
  */
abstract class ServiceAccessLayer(
    host: String,
    port: Int,
    securedConnection: Boolean = false)(implicit val actorSystem: ActorSystem)
    extends ClientBase {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import SprayJsonSupport._

  // The HTTP scheme type should be configurable from the constructor
  private val httpSchme = Uri.httpScheme()

  def RootUri =
    Uri.from(host = host,
             port = port,
             scheme = Uri.httpScheme(securedConnection))
}
