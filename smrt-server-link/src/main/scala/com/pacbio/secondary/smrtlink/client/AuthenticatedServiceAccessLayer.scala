package com.pacbio.secondary.smrtlink.client

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import akka.actor.ActorSystem
import akka.util.Timeout
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.client.RequestBuilding._

/**
  * FIXME. This is broken.
  */
class AuthenticatedServiceAccessLayer(
    host: String,
    port: Int,
    token: String,
    wso2Port: Int = 8243,
    securedConnection: Boolean = true)(implicit actorSystem: ActorSystem)
    extends SmrtLinkServiceClient(host, port)(actorSystem)
    with ApiManagerClientBase {

  implicit val timeout: Timeout = 30.seconds

  override def RootUri: Uri =
    Uri.from(host = host,
             port = wso2Port,
             scheme = Uri.httpScheme(securedConnection))

  lazy val RootAuthUriPath
    : Uri.Path = Uri.Path./ ++ Uri.Path("SMRTLink") / "1.0.0"

  override def toUri(path: Uri.Path) =
    RootUri.copy(path = RootAuthUriPath ++ Uri.Path./ ++ path)

  def addAuthHeader(request: HttpRequest): HttpRequest =
    request ~> addHeader("Authorization", s"Bearer $token")
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

  // FIXME this runs but the result isn't working properly
  def apply(host: String, port: Int, user: String, password: String)(
      implicit actorSystem: ActorSystem) = {
    implicit val ec = actorSystem.dispatcher
    val wso2Client =
      new ApiManagerAccessLayer(host, user = user, password = password)
    val tx = for {
      reg <- wso2Client.register()
      auth <- wso2Client.login(reg.clientId, reg.clientSecret, clientScopes)
    } yield auth
    Try { Await.result(tx, 30.seconds) } match {
      case Success(auth) =>
        println(s"$auth")
        new AuthenticatedServiceAccessLayer(host, port, auth.access_token)(
          actorSystem)
      case Failure(err) =>
        throw new RuntimeException(s"Can't authenticate: $err")
    }
  }
}
