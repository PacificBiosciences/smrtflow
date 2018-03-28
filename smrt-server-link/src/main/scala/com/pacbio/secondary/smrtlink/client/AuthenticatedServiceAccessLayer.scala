package com.pacbio.secondary.smrtlink.client

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}
import java.net.URL

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.LazyLogging

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

  private def doApply(
      host: String,
      port: Int,
      user: String,
      password: String,
      toClientAuth: (ApiManagerAccessLayer) => Future[(String, String)])(
      implicit actorSystem: ActorSystem): AuthenticatedServiceAccessLayer = {
    implicit val ec = actorSystem.dispatcher
    val wso2Client =
      new ApiManagerAccessLayer(host, user = user, password = password)
    val tx = for {
      (clientId, clientSecret) <- toClientAuth(wso2Client)
      auth <- wso2Client.login(clientId, clientSecret, clientScopes)
    } yield auth
    Try { Await.result(tx, 30.seconds) } match {
      case Success(auth) =>
        new AuthenticatedServiceAccessLayer(host, port, auth.access_token)(
          actorSystem)
      case Failure(err) =>
        throw new RuntimeException(s"Can't authenticate: $err")
    }
  }

  // FIXME this runs but the result isn't working properly
  def apply(host: String, port: Int, user: String, password: String)(
      implicit actorSystem: ActorSystem): AuthenticatedServiceAccessLayer = {
    def toClientAuth(wso2Client: ApiManagerAccessLayer) = {
      implicit val ec = actorSystem.dispatcher
      wso2Client.register().map(reg => (reg.clientId, reg.clientSecret))
    }
    doApply(host, port, user, password, toClientAuth)(actorSystem)
  }

  def apply(host: String,
            port: Int,
            user: String,
            password: String,
            clientId: String,
            clientSecret: String)(
      implicit actorSystem: ActorSystem): AuthenticatedServiceAccessLayer = {
    def toClientAuth(wso2Client: ApiManagerAccessLayer) =
      Future.successful((clientId, clientSecret))
    doApply(host, port, user, password, toClientAuth)(actorSystem)
  }
}
