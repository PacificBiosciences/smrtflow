package com.pacbio.secondary.smrtlink.client

import scala.concurrent.Future
import scala.concurrent.duration._
import com.typesafe.scalalogging.LazyLogging
import akka.actor.ActorSystem
import akka.util.Timeout
import akka.http.scaladsl.server._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, Uri}
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
    request ~> addHeader(Authorization(OAuth2BearerToken(token)))

  override def sendRequest(request: HttpRequest): Future[HttpResponse] =
    AkkaSource
      .single(addAuthHeader(request))
      .via(https)
      .runWith(Sink.head)
}

object AuthenticatedServiceAccessLayer extends LazyLogging {
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
    val wso2Client =
      new ApiManagerAccessLayer(host, user = user, password = password)(
        actorSystem)
    wso2Client
      .login(clientScopes)
      .map { auth =>
        logger.debug(
          s"Auth token ${auth.access_token} use header 'Authorization:Bearer ${auth.access_token}'")
        new AuthenticatedServiceAccessLayer(host, port, auth.access_token)(
          actorSystem)
      }(actorSystem.dispatcher)
  }
}

trait SmrtLinkClientProvider extends LazyLogging {

  protected def getPass: String = {
    val standardIn = System.console()
    print("Password: ")
    standardIn.readPassword().mkString("")
  }

  def getClient(host: String,
                port: Int,
                user: Option[String] = None,
                password: Option[String] = None,
                authToken: Option[String] = None,
                usePassword: Boolean = false)(
      implicit actorSystem: ActorSystem): Future[SmrtLinkServiceClient] = {

    def toClient = {
      if (host != "localhost") {
        Future.failed(new IllegalArgumentException(
          "Authentication required when connecting to a remote SMRT Link server.  Please specify a username (--user or PB_SERVICE_AUTH_USER environment variable) and a password (--ask-pass, --password, or PB_SERVICE_AUTH_PASSWORD environment variable)."))
      } else {
        Future.successful {
          new SmrtLinkServiceClient(host, port)(actorSystem)
        }
      }
    }

    def toAuthClient(t: String) = {
      logger.info("Will route through WSO2 with user-supplied auth token")
      Future.successful {
        new AuthenticatedServiceAccessLayer(host, port, t)(actorSystem)
      }
    }

    def toAuthClientLogin(u: String, p: String) = {
      logger.info("Will authenticate with WSO2")
      AuthenticatedServiceAccessLayer.getClient(host, port, u, p)(actorSystem)
    }

    authToken match {
      case Some(t) => toAuthClient(t)
      case None =>
        (user, password) match {
          case (Some(u), Some(p)) => toAuthClientLogin(u, p)
          case (Some(u), None) => {
            if (usePassword) toAuthClientLogin(u, getPass)
            else toClient
          }
          case _ => toClient
        }
    }
  }
}
