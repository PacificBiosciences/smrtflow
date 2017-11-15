package com.pacbio.secondary.smrtlink.client

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.wso2.carbon.apimgt.rest.api.{publisher, store}
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.BasicMarshallers._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import scala.xml._

trait ApiManagerClientBase {
  val ADMIN_PORT = 9443
  val API_PORT = 8243

  implicit val sslContext = {
    // Create a trust manager that does not validate certificate chains.
    val permissiveTrustManager: TrustManager = new X509TrustManager() {
      override def checkClientTrusted(chain: Array[X509Certificate],
                                      authType: String): Unit = {}
      override def checkServerTrusted(chain: Array[X509Certificate],
                                      authType: String): Unit = {}
      override def getAcceptedIssuers(): Array[X509Certificate] = {
        null
      }
    }

    val initTrustManagers = Array(permissiveTrustManager)
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, initTrustManagers, new SecureRandom())
    ctx
  }

}

class ApiManagerAccessLayer(
    host: String,
    portOffset: Int = 0,
    user: String,
    password: String)(implicit actorSystem: ActorSystem)
    extends ApiManagerClientBase
    with LazyLogging {

  import ApiManagerJsonProtocols._
  import SprayJsonSupport._
  import Wso2Models._

  implicit val executionContext = actorSystem.dispatcher
  implicit val timeout: Timeout = 200.seconds

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

  protected def getWso2Connection(portNumber: Int) = {
    IO(Http) ? Http.HostConnectorSetup(host,
                                       port = portNumber + portOffset,
                                       sslEncryption = true)
  }

  // request pipeline for the API port
  val apiPipe: Future[SendReceive] = {
    for (Http.HostConnectorInfo(connector, _) <- getWso2Connection(API_PORT))
      yield sendReceive(connector)
  }

  // request pipeline for the admin port
  val adminPipe: Future[SendReceive] =
    for (Http.HostConnectorInfo(connector, _) <- getWso2Connection(ADMIN_PORT))
      yield sendReceive(connector)

  def register(): Future[ClientRegistrationResponse] = {
    val body = ClientRegistrationRequest("foo",
                                         Random.alphanumeric.take(20).mkString,
                                         "Production",
                                         user,
                                         "password refresh_token",
                                         true)

    val request = (
      Post(s"/client-registration/v0.10/register", body)
        ~> addCredentials(BasicHttpCredentials(user, password))
    )

    adminPipe.flatMap(_(request)).map(unmarshal[ClientRegistrationResponse])
  }

  // the consumer key and secret can come from the dynamic client
  // registration mechanism (the register() method) or they can come
  // from some existing configuration, like the UI's app-config.json
  def login(consumerKey: String,
            consumerSecret: String,
            scopes: Set[String]): Future[OauthToken] = {
    val body = FormData(
      Map(
        "grant_type" -> "password",
        "username" -> user,
        "password" -> password,
        "scope" -> scopes.mkString(" ")
      ))

    val request = (
      Post(s"/token", body)
        ~> addCredentials(BasicHttpCredentials(consumerKey, consumerSecret))
    )
    apiPipe.flatMap(_(request)).map(unmarshal[OauthToken])
  }

  /**
    * Mechanism to see if wso2 has "completely" started up
    * and is functioning. These makes a few calls to a different
    * endpoints to make sure it's doing something reasonable.
    *
    * This may need to be improved to call other endpoints.
    *
    * @param tries Number of retries
    * @param delay Delay between requests
    * @return
    */
  def waitForStart(tries: Int = 40,
                   delay: FiniteDuration = 10.seconds): Future[String] = {

    implicit val timeout = tries * delay

    // Wait for token, store, and publisher APIs to start.
    // Before they're started, there'll be a failed connection attempt,
    // a 500 status response, or a 404 status response

    for {
      _ <- waitForRequest(Get("/token"), apiPipe, tries, delay)
      _ <- waitForRequest(Get("/api/am/store/v0.10/applications"),
                          adminPipe,
                          tries,
                          delay)
      _ <- waitForRequest(Get("/api/am/publisher/v0.10/apis"),
                          adminPipe,
                          tries,
                          delay)
    } yield "Successfully Started up WSO2"

  }

  def waitForRequest(
      request: HttpRequest,
      pipeline: Future[SendReceive],
      tries: Int = 40,
      delay: FiniteDuration = 10.seconds): Future[HttpResponse] = {
    implicit val timeout: Timeout = tries * delay

    val fut = pipeline.flatMap(_(request))
    def retry =
      akka.pattern.after(delay, using = actorSystem.scheduler)(
        waitForRequest(request, pipeline, tries - 1, delay))

    val expectedStatuses: Set[StatusCode] =
      Set(StatusCodes.OK,
          StatusCodes.Unauthorized,
          StatusCodes.MethodNotAllowed)

    fut
      .recoverWith({
        case exc: Http.ConnectionAttemptFailedException => {
          if (tries > 1) {
            retry
          } else {
            Future.failed(exc)
          }
        }
      })
      .flatMap(response => {
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

  // Store APIs
  def putApplication(app: store.models.Application,
                     token: OauthToken): Future[HttpResponse] = {
    val request = (
      Put(s"/api/am/store/v0.10/applications/${app.applicationId.get}", app)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request))
  }

  def getApplication(applicationId: String,
                     token: OauthToken): Future[store.models.Application] = {
    val request = (
      Get(s"/api/am/store/v0.10/applications/${applicationId}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[store.models.Application])
  }

  def searchApplications(
      name: String,
      token: OauthToken): Future[store.models.ApplicationList] = {
    val request = (
      Get(s"/api/am/store/v0.10/applications?query=${name}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[store.models.ApplicationList])
  }

  def subscribe(apiId: String,
                applicationId: String,
                tier: String,
                token: OauthToken): Future[store.models.Subscription] = {
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
  def searchApis(name: String,
                 token: OauthToken): Future[publisher.models.APIList] = {
    val request = (
      Get(s"/api/am/publisher/v0.10/apis?query=${name}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[publisher.models.APIList])
  }

  def getApiDetails(id: String,
                    token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Get(s"/api/am/publisher/v0.10/apis/${id}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[publisher.models.API])
  }

  def putApiDetails(api: publisher.models.API,
                    token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Put(s"/api/am/publisher/v0.10/apis/${api.id.get}", api)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[publisher.models.API])
  }

  def postApiDetails(api: publisher.models.API,
                     token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Post(s"/api/am/publisher/v0.10/apis", api)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request)).map(unmarshal[publisher.models.API])
  }

  def apiChangeLifecycle(apiId: String,
                         action: ApiLifecycleAction,
                         token: OauthToken): Future[HttpResponse] = {
    val request = (
      Post(
        s"/api/am/publisher/v0.10/apis/change-lifecycle?apiId=${apiId}&action=${action.toString}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe.flatMap(_(request))
  }

  // User Store admin API
  val userStoreUrl =
    "/services/RemoteUserStoreManagerService.RemoteUserStoreManagerServiceHttpsSoap12Endpoint"

  def soapCall(action: String,
               content: Elem,
               user: String,
               password: String): Future[NodeSeq] = {
    val body =
      <soap-env:Envelope xmlns:soap-env='http://schemas.xmlsoap.org/soap/envelope/'>
                 <soap-env:Body>
                   {content}
                 </soap-env:Body>
               </soap-env:Envelope>
    val request = (
      Post(userStoreUrl, body)
        ~> addCredentials(BasicHttpCredentials(user, password))
        ~> addHeader("SOAPAction", action)
    )
    adminPipe
      .flatMap(_(request))
      .map(unmarshal[NodeSeq])
  }

  def getRoleNames(user: String, password: String): Future[Seq[String]] = {
    val params =
      <wso2um:getRoleNames xmlns:wso2um='http://service.ws.um.carbon.wso2.org'>
                 </wso2um:getRoleNames>
    soapCall("urn:getRoleNames", params, user, password)
      .map(x => (x \ "Body" \ "getRoleNamesResponse" \ "return").map(_.text))
  }

  def addRole(user: String, password: String, role: String): Future[NodeSeq] = {
    val params =
      <wso2um:addRole xmlns:wso2um='http://service.ws.um.carbon.wso2.org'>
                   <wso2um:roleName>{role}</wso2um:roleName>
                 </wso2um:addRole>
    soapCall("urn:addRole", params, user, password)
  }

  def getUserListOfRole(user: String,
                        password: String,
                        role: String): Future[Seq[String]] = {
    val params =
      <wso2um:getUserListOfRole xmlns:wso2um='http://service.ws.um.carbon.wso2.org'>
                   <wso2um:roleName>{role}</wso2um:roleName>
                 </wso2um:getUserListOfRole>
    soapCall("urn:getUserListOfRole", params, user, password)
      .map(x =>
        (x \ "Body" \ "getUserListOfRoleResponse" \ "return").map(_.text))
  }

  def updateUserListOfRole(user: String,
                           password: String,
                           role: String,
                           users: Seq[String]): Future[NodeSeq] = {
    val newUserList = users.map(u => <wso2um:newUsers>{u}</wso2um:newUsers>)
    val params =
      <wso2um:updateUserListOfRole xmlns:wso2um='http://service.ws.um.carbon.wso2.org'>
                   <wso2um:roleName>{role}</wso2um:roleName>
                   {newUserList}
                 </wso2um:updateUserListOfRole>
    soapCall("urn:updateUserListOfRole", params, user, password)
  }
}
