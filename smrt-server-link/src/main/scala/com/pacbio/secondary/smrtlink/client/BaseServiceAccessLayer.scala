package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.Http
import akka.pattern.after
import com.pacbio.secondary.smrtlink.models.ServiceStatus
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  StatusCode,
  StatusCodes
}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

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

class ServiceAccessLayer(val baseUrl: URL)(implicit actorSystem: ActorSystem)
    extends Retrying {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import SprayJsonSupport._

  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher

  val http = Http()

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }

  protected def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString
  protected def toUiRootUrl(port: Int): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, port, "/").toString

  /**
    * Provide a list of relative URLs that GET requests will be
    * can be checked via {{{checkServiceEndpoints}}}
    *
    * Override this in the subclasses
    * @return
    */
  def serviceStatusEndpoints: Vector[String] = Vector()

  // Pipelines and serialization
//  protected def requestPipe: HttpRequest => Future[HttpResponse] = sendReceive
//  protected def respPipeline: HttpRequest => Future[HttpResponse] = requestPipe
//  protected def rawDataPipeline: HttpRequest => Future[Array[Byte]] =
//    requestPipe ~> unmarshal[Array[Byte]]
//  // XXX This is misnamed - it could just as easily be XML or plaintext
//  protected def rawJsonPipeline: HttpRequest => Future[String] =
//    requestPipe ~> unmarshal[String]
//  protected def serviceStatusPipeline: HttpRequest => Future[ServiceStatus] =
//    requestPipe ~> unmarshal[ServiceStatus]

  // We should try to standardize on nomenclature here, 'Segment' for relative and
  // and 'Endpoint' for absolute URL?
  val statusUrl = toUrl("/status")

  /**
    * Get Status of the System. The model must adhere to the SmrtServer Status
    * message schema.
    *
    * @return
    */
  def getStatus(): Future[ServiceStatus] =
    http.singleRequest(Get(statusUrl)).flatMap(Unmarshal(_).to[ServiceStatus])

  def getEndpoint(endpointUrl: String): Future[HttpResponse] =
    http.singleRequest(Get(endpointUrl))

  def getServiceEndpoint(endpointPath: String): Future[HttpResponse] =
    http.singleRequest(Get(toUrl(endpointPath)))

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

  /**
    * Check an endpoint for status 200
    *
    * @param endpointPath Provided as Relative to the base url in Client.
    * @return
    */
  def checkServiceEndpoint(endpointPath: String): Int =
    checkEndpoint(toUrl(endpointPath))

  /**
    * Check the UI webserver for "Status"
    *
    * @param uiPort UI webserver port
    * @return
    */
  def checkUiEndpoint(uiPort: Int): Int = checkEndpoint(toUiRootUrl(uiPort))

  /**
    * Run over each defined Endpoint (provided as relative segments to the base)
    *
    * Will NOT fail early. It will run over all endpoints and return non-zero
    * if the any of the results have failed.
    *
    * Note, this is blocking.
    *
    * @return
    */
  def checkServiceEndpoints: Int = {

    serviceStatusEndpoints
      .map(checkServiceEndpoint)
      .foldLeft(0) { (a, v) =>
        Seq(a, v).max
      }
  }

  def callWithBlockingRetry[A, B](f: (A => Future[B]),
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

  // This should have a backoff model to wait a few seconds before the retry. It should
  // also have a better error message that includes the total number of retries
  def callWithRetry[A, B](f: (A => Future[B]),
                          input: A,
                          numRetries: Int): Future[B] = {
    f(input).recoverWith {
      case NonFatal(_) if numRetries > 0 =>
        callWithRetry[A, B](f, input, numRetries - 1)
    }
  }

  def getStatusWithRetry(
      maxRetries: Int = 3,
      retryDelay: FiniteDuration = 1.second): Future[ServiceStatus] =
    retry[ServiceStatus](getStatus, retryDelay, maxRetries)(
      actorSystem.dispatcher,
      actorSystem.scheduler)

}
