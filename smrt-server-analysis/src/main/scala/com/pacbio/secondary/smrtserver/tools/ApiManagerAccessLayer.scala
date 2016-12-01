package com.pacbio.secondary.smrtserver.tools

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, X509TrustManager, TrustManager}

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Random

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout

import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.httpx.marshalling.BasicMarshallers._
import spray.httpx.SprayJsonSupport
import spray.json._

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import org.wso2.carbon.apimgt.rest.api.store
import org.wso2.carbon.apimgt.rest.api.publisher


class ApiManagerAccessLayer(host: String, portOffset: Int = 0, user: String, pass: String)(implicit actorSystem: ActorSystem) extends LazyLogging {

  import actorSystem.dispatcher

  import ApiManagerJsonProtocols._
  import SprayJsonSupport._

  implicit val timeout: Timeout = 200.seconds

  val ADMIN_PORT = 9443
  val API_PORT = 8243

  // this enum isn't described in the wso2 swagger, so I'm including a
  // hand-written version here
  type ApiLifecycleAction = ApiLifecycleAction.Value

  object ApiLifecycleAction extends Enumeration {
    val PUBLISH = Value("Publish")
    val DEPLOY_AS_PROTOTYPE = Value("Deploy as a Prototype")
    val DEMOTE_TO_CREATED = Value("Demote to Created")
    val DEMOTE_TO_PROTOTYPED = Value("Demote to Prototyped")
    val BLOCK = Value("Block")
    val DEPRECATE = Value("Deprecate")
    val RE_PUBLISH = Value("Re-Publish")
    val RETIRE = Value("Retire")
  }

  implicit val sslContext = {
    // Create a trust manager that does not validate certificate chains.
    val permissiveTrustManager: TrustManager = new X509TrustManager() {
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {
      }
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {
      }
      override def getAcceptedIssuers(): Array[X509Certificate] = {
        null
      }
    }

    val initTrustManagers = Array(permissiveTrustManager)
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, initTrustManagers, new SecureRandom())
    ctx
  }

  // request pipeline for the admin port
  val adminPipe: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <-
        IO(Http) ? Http.HostConnectorSetup(host, port = ADMIN_PORT + portOffset, sslEncryption = true)
    ) yield sendReceive(connector)

  // request pipeline for the API port
  val apiPipe: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <-
        IO(Http) ? Http.HostConnectorSetup(host, port = API_PORT + portOffset, sslEncryption = true)
    ) yield sendReceive(connector)

  def register(): Future[ClientRegistrationResponse] = {
    val body = ClientRegistrationRequest("foo", Random.alphanumeric.take(20).mkString, "Production", user, "password refresh_token", true)

    val request = (
      Post(s"/client-registration/v0.10/register", body)
        ~> addCredentials(BasicHttpCredentials(user, pass))
    )

    adminPipe.flatMap(_(request)).map(unmarshal[ClientRegistrationResponse])
  }

  def waitForStart(tries: Int = 20, delay: FiniteDuration = 10.seconds): Future[Seq[HttpResponse]] = {
    implicit val timeout = tries * delay

    // Wait for token, store, and publisher APIs to start.
    // Before they're started, there'll be a failed connection attempt,
    // a 500 status response, or a 404 status response
    val requests = List(
      (Get("/token"), apiPipe),
      (Get("/api/am/store/v0.10/applications"), adminPipe),
      (Get("/api/am/publisher/v0.10/apis"), adminPipe))

    Future.sequence(requests.map(req => waitForRequest(req._1, req._2, tries, delay)))
  }

  def waitForRequest(request: HttpRequest, pipeline: Future[SendReceive], tries: Int = 20, delay: FiniteDuration = 10.seconds): Future[HttpResponse] = {
    implicit val timeout: Timeout = tries * delay

    val fut = pipeline.flatMap(_(request))
    def retry = akka.pattern.after(delay, using = actorSystem.scheduler)(waitForRequest(request, pipeline, tries - 1, delay))

    val expectedStatuses: Set[StatusCode] =
      Set(StatusCodes.OK, StatusCodes.Unauthorized, StatusCodes.MethodNotAllowed)

    fut.recoverWith({
      case exc: Http.ConnectionAttemptFailedException => {
        if (tries > 1) {
          retry
        } else {
          Future.failed(exc)
        }
      }
    }).flatMap(response => {
      if (expectedStatuses.contains(response.status)) {
        Future.successful(response)
      } else {
        if (tries > 1) {
          retry
        } else {
          Future.failed(new Exception("server didn't come up in time"))
        }
      }
    })
  }

  // the consumer key and secret can come from the dynamic client
  // registration mechanism (the register() method) or they can come
  // from some existing configuration, like the UI's app-config.json
  def login(consumerKey: String, consumerSecret: String, scopes: Set[String]): Future[OauthToken] = {
    val body = FormData(Map(
      "grant_type" -> "password",
      "username" -> user,
      "password" -> pass,
      "scope" -> scopes.mkString(" ")
    ))

    val request = (
      Post(s"/token", body)
        ~> addCredentials(BasicHttpCredentials(consumerKey, consumerSecret))
    )
    apiPipe.flatMap(_(request)).map(unmarshal[OauthToken])
  }

  // Store APIs
  def putApplication(app: store.models.Application, token: OauthToken): Future[HttpResponse] = {
    val request = (
      Put(s"/api/am/store/v0.10/applications/${app.applicationId.get}", app)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request))
  }

  def getApplication(applicationId: String, token: OauthToken): Future[store.models.Application] = {
    val request = (
      Get(s"/api/am/store/v0.10/applications/${applicationId}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[store.models.Application])
  }

  def searchApplications(name: String, token: OauthToken): Future[store.models.ApplicationList] = {
    val request = (
      Get(s"/api/am/store/v0.10/applications?query=${name}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[store.models.ApplicationList])
  }

  def subscribe(apiId: String, applicationId: String, tier: String, token: OauthToken): Future[store.models.Subscription] = {
    val subscription = store.models.Subscription(
      subscriptionId = None,
      tier = tier,
      apiIdentifier = apiId,
      applicationId = applicationId,
      status = None
    )

    val request = (
      Post(s"/api/am/store/v0.10/subscriptions", subscription)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[store.models.Subscription])
  }


  // publisher APIs
  def searchApis(name: String, token: OauthToken): Future[publisher.models.APIList] = {
    val request = (
      Get(s"/api/am/publisher/v0.10/apis?query=${name}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[publisher.models.APIList])
  }

  def getApiDetails(id: String, token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Get(s"/api/am/publisher/v0.10/apis/${id}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[publisher.models.API])
  }

  def putApiDetails(api: publisher.models.API, token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Put(s"/api/am/publisher/v0.10/apis/${api.id.get}", api)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[publisher.models.API])
  }

  def postApiDetails(api: publisher.models.API, token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Post(s"/api/am/publisher/v0.10/apis", api)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[publisher.models.API])
  }

  def apiChangeLifecycle(apiId: String, action: ApiLifecycleAction, token: OauthToken): Future[HttpResponse] = {
    val request = (
      Post(s"/api/am/publisher/v0.10/apis/change-lifecycle?apiId=${apiId}&action=${action.toString}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request))
  }
}
