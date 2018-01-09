package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  StatusCode,
  StatusCodes
}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.http.scaladsl.Http
import akka.pattern.after
import akka.stream.ActorMaterializer

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._
//import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import com.pacbio.secondary.smrtlink.models.ServiceStatus

// Move this to a central location
trait UrlUtils {
  def convertToUrl(host: String, port: Int) = {
    val h = host.replaceFirst("http://", "")
    new URL(s"http://$h:$port")
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

  val baseUrl: URL

  // This should really return a URL instance, not a string
  protected def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol,
            baseUrl.getHost,
            baseUrl.getPort,
            baseUrl.getPath + segment).toString

  val statusUrl = toUrl("/status")

  protected def getEndpoint(endpointUrl: String): Future[HttpResponse] =
    http.singleRequest(Get(endpointUrl))

  protected def getResponse(request: HttpRequest): Future[HttpResponse] =
    http
      .singleRequest(request)
      .flatMap { response =>
        response.status match {
          case StatusCodes.OK | StatusCodes.Created =>
            Future.successful(response)
          case _ =>
            Future.failed(
              new Exception(
                s"HTTP ERROR ${response.status}: ${response.toString}"))
        }
      }

  protected def getObject[T](request: HttpRequest)(
      implicit um: Unmarshaller[HttpResponse, T]): Future[T] =
    getResponse(request).flatMap(um(_))

  /**
    * Get Status of the System. The model must adhere to the SmrtServer Status
    * message schema.
    *
    * @return
    */
  def getStatus(): Future[ServiceStatus] =
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
  def checkEndpoint(endpointUrl: String,
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
abstract class ServiceAccessLayer(val baseUrl: URL)(
    implicit val actorSystem: ActorSystem)
    extends ClientBase {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import SprayJsonSupport._

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }
}
