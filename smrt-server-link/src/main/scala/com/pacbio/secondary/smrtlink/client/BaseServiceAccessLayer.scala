package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.Http
import akka.pattern.after
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  StatusCode,
  StatusCodes,
  Uri
}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import com.pacbio.secondary.smrtlink.models.ServiceStatus

class ServiceAccessLayer(val baseUrl: URL)(
    implicit val actorSystem: ActorSystem)
    extends ClientBase {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import SprayJsonSupport._

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }

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

  def getServiceEndpoint(endpointPath: String): Future[HttpResponse] =
    http.singleRequest(Get(toUrl(endpointPath)))

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
}
