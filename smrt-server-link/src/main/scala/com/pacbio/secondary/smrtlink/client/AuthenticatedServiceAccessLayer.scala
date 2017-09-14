package com.pacbio.secondary.smrtlink.client

import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}

import scala.concurrent.{Await,Future}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport


class AuthenticatedServiceAccessLayer(
      baseUrl: URL,
      token: String,
      wso2Port: Int = 8243)
    (implicit actorSystem: ActorSystem)
    extends SmrtLinkServiceAccessLayer(baseUrl, None)(actorSystem)
    with ApiManagerClientBase {

  implicit val timeout: Timeout = 30.seconds

  def this(host: String, port: Int, token: String)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port), token)(actorSystem)
  }

  override def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, wso2Port, s"/SMRTLink/1.0.0$segment").toString

  private def sslSendReceive(request: HttpRequest): Future[HttpResponse] = {
    for {
      Http.HostConnectorInfo(connector, _) <- IO(Http) ? Http.HostConnectorSetup(baseUrl.getHost, port = wso2Port, sslEncryption = true)
      response <- connector ? request
    } yield response match {
      case r: HttpResponse => r
      case x => throw new RuntimeException(s"Unexpected response $x")
    }
  }

  override def requestPipe: HttpRequest => Future[HttpResponse] =
    addHeader("Authorization", s"Bearer ${token}") ~> sslSendReceive
}

object AuthenticatedServiceAccessLayer {
  private val clientScopes = Set("welcome", "sample-setup", "run-design", "run-qc", "data-management", "analysis", "openid", "userinfo")

  // FIXME this runs but the result isn't working properly
  def apply(host: String, port: Int, user: String, password: String)
           (implicit actorSystem: ActorSystem) = {
    implicit val ec = actorSystem.dispatcher
    val wso2Client = new ApiManagerAccessLayer(host, user = user, password = password)
    val tx = for {
      reg <- wso2Client.register()
      auth <- wso2Client.login(reg.clientId, reg.clientSecret, clientScopes)
    } yield auth
    Try { Await.result(tx, 30.seconds) } match {
      case Success(auth) =>
        println(s"$auth")
        new AuthenticatedServiceAccessLayer(host, port, auth.access_token)(actorSystem)
      case Failure(err) =>
        throw new RuntimeException(s"Can't authenticate: $err")
    }
  }
}
