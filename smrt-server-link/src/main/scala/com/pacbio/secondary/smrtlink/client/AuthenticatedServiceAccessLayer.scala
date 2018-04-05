package com.pacbio.secondary.smrtlink.client

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}
import java.net.URL

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.LazyLogging
import spray.json

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpRequest, Uri, HttpResponse}
import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.scaladsl.{Sink, Source => AkkaSource}

class AuthenticatedServiceAccessLayer(
    host: String,
    port: Int,
    token: String,
    wso2Port: Int = 8243,
    securedConnection: Boolean = true)(implicit actorSystem: ActorSystem)
    extends SmrtLinkServiceClient(host, port)(actorSystem)
    with SecureClientBase {

  implicit val timeout: Timeout = 30.seconds
  lazy val https = getHttpsConnection(host, wso2Port)

  override def RootUri: Uri =
    Uri.from(host = host,
             port = wso2Port,
             scheme = Uri.httpScheme(securedConnection))

  lazy val RootAuthUriPath
    : Uri.Path = Uri.Path./ ++ Uri.Path("SMRTLink") / "1.0.0"

  override def toUri(path: Uri.Path) = {
    RootUri.copy(path = RootAuthUriPath ++ Uri.Path./ ++ path)
  }

  private def addAuthHeader(request: HttpRequest): HttpRequest =
    request ~> addHeader("Authorization", s"Bearer ${token}")

  override def sendRequest(request: HttpRequest): Future[HttpResponse] =
    AkkaSource
      .single(addAuthHeader(request))
      .via(https)
      .runWith(Sink.head)
}

object AuthenticatedServiceAccessLayer {
  private val clientScopes = Set("welcome",
                                 "sample-setup",
                                 "run-design",
                                 "run-qc",
                                 "data-management",
                                 "analysis",
                                 "openid",
                                 "userinfo")

  def getClient(host: String, port: Int, user: String, password: String)(
      implicit actorSystem: ActorSystem)
    : Future[AuthenticatedServiceAccessLayer] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val wso2Client =
      new ApiManagerAccessLayer(host, user = user, password = password)(
        actorSystem)
    wso2Client
      .login(clientScopes)
      .map { auth =>
        new AuthenticatedServiceAccessLayer(host, port, auth.access_token)(
          actorSystem)
      }
      .recover {
        case e: spray.json.DeserializationException =>
          throw new AuthenticationError(
            "Authentication failed.  Please check that the username and password are correct.")
      }
  }
}
