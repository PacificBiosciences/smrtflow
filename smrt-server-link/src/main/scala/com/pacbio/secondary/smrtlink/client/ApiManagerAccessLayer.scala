package com.pacbio.secondary.smrtlink.client

import javax.net.ssl._
import java.net.URL

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.wso2.carbon.apimgt.rest.api.{publisher, store}
import spray.json
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.headers.{
  Accept,
  BasicHttpCredentials,
  `Content-Type`
}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Sink, Source => AkkaSource}
import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal
import scala.xml._

class ApiManagerAccessLayer(
    host: String,
    portOffset: Int = 0,
    user: String,
    password: String)(implicit val actorSystem: ActorSystem)
    extends SecureClientBase
    with LazyLogging {

  import ApiManagerJsonProtocols._
  import SprayJsonSupport._
  import Wso2Models._

  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  implicit val timeout: Timeout = 200.seconds

  val ADMIN_PORT = 9443
  val API_PORT = 8243
  val EP_STATUS = "/publisher"

  val HEADER_CONTENT_JSON = `Content-Type`(ContentTypes.`application/json`)
  val HEADER_ACCEPT_JSON = Accept(MediaRange(MediaTypes.`application/json`))

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

  val apiPipeHttp = getHttpsConnection(host, API_PORT + portOffset)
  val adminPipeHttp = getHttpsConnection(host, ADMIN_PORT + portOffset)

  // This needs the correct HTTPS configuration
  def apiPipe(request: HttpRequest): Future[HttpResponse] =
    AkkaSource
      .single(request)
      .via(apiPipeHttp)
      .runWith(Sink.head)

  def adminPipe(request: HttpRequest): Future[HttpResponse] =
    AkkaSource
      .single(request)
      .via(adminPipeHttp)
      .runWith(Sink.head)

  /**
    * If we can connect to publisher at :9443/publisher on https, then
    * this is assumed to be "Successful" status
    *
    * @param numRetries Number of retries
    * @return
    */
  def getStatus(numRetries: Int = 3): Future[String] = {
    apiPipe(Get(EP_STATUS))
      .map(_ => "Successfully connected to publisher")
  }

  def register(): Future[ClientRegistrationResponse] = {
    val body = ClientRegistrationRequest("foo",
                                         Random.alphanumeric.take(20).mkString,
                                         "Production",
                                         user,
                                         "password refresh_token",
                                         true)

    val request = (
      Post("/client-registration/v0.10/register", body)
        ~> addCredentials(BasicHttpCredentials(user, password))
    )

    adminPipe(request)
      .flatMap(Unmarshal(_).to[ClientRegistrationResponse])

  }

  // in practice the key and secret are now hardcoded
  def login(consumerKey: String,
            consumerSecret: String,
            scopes: Set[String]): Future[OauthToken] = {

    // These need to be URL encoded, or does the library do this automagically?
    val body = FormData(
      "grant_type" -> "password",
      "username" -> user,
      "password" -> password,
      "scope" -> scopes.mkString(" ")
    )

    // This is not really clear to me how this API works.
    // The call body.toEntity is important and will generate the correct content type
    // of application/x-www-form-urlencoded. Otherwise, it will be set as
    // application/json.
    val request = (
      Post("/token", body.toEntity)
        ~> addCredentials(BasicHttpCredentials(consumerKey, consumerSecret))
      //~> logRequest((r: HttpRequest) => println(s"Request $r"))
    )
    apiPipe(request)
      .flatMap(Unmarshal(_).to[OauthToken])
      .recover {
        case e: spray.json.DeserializationException =>
          throw new AuthenticationError(
            "Authentication failed.  Please check that the username and password are correct.")
      }
  }

  def login(scopes: Set[String]): Future[OauthToken] =
    login(defaultClient.clientId, defaultClient.clientSecret, scopes)

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
  def waitForStart(tries: Int, delay: FiniteDuration): Future[String] = {

    // Wait for token, store, and publisher APIs to start.
    // Before they're started, there'll be a failed connection attempt,
    // a 500 status response, or a 404 status response

    // For the token API, a (405) Method Not Allowed will be raised
    // For applications and apis, an (401) Unauthorized request will be raised

    for {
      //_ <- waitForRequest(Get("/token"), apiPipe, tries, delay)
      _ <- waitForRequest(Get("/api/am/store/v0.10/applications"),
                          tries,
                          delay)
      _ <- waitForRequest(Get("/api/am/publisher/v0.10/apis"), tries, delay)
    } yield "Successfully Connected to WSO2"

  }

  def waitForRequest(request: HttpRequest,
                     tries: Int,
                     delay: FiniteDuration): Future[HttpResponse] = {

    def retry =
      akka.pattern.after(delay, using = actorSystem.scheduler)(
        waitForRequest(request, tries - 1, delay))

    val expectedStatuses: Set[StatusCode] =
      Set(StatusCodes.OK,
          StatusCodes.Unauthorized,
          StatusCodes.MethodNotAllowed)

    adminPipe(request)
      .recoverWith({
        case NonFatal(exc) => { // ConnectionAttemptFailedException
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

    adminPipe(request)
  }

  def getApplication(applicationId: String,
                     token: OauthToken): Future[store.models.Application] = {
    val request = (
      Get(s"/api/am/store/v0.10/applications/$applicationId")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )

    adminPipe(request)
      .flatMap(Unmarshal(_).to[store.models.Application])
  }

  def searchApplications(
      name: String,
      token: OauthToken): Future[store.models.ApplicationList] = {

    val request = (
      Get(s"/api/am/store/v0.10/applications?query=$name")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )

    adminPipe(request)
      .flatMap(Unmarshal(_).to[store.models.ApplicationList])
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
      Post("/api/am/store/v0.10/subscriptions", subscription)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe(request)
      .flatMap(Unmarshal(_).to[store.models.Subscription])
  }

  // publisher APIs
  def searchApis(name: String,
                 token: OauthToken): Future[publisher.models.APIList] = {
    val request = (
      Get(s"/api/am/publisher/v0.10/apis?query=${name}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )

    adminPipe(request)
      .flatMap(Unmarshal(_).to[publisher.models.APIList])
  }

  def getApiDetails(id: String,
                    token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Get(s"/api/am/publisher/v0.10/apis/${id}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )

    adminPipe(request)
      .flatMap(Unmarshal(_).to[publisher.models.API])
  }

  def putApiDetails(api: publisher.models.API,
                    token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Put(s"/api/am/publisher/v0.10/apis/${api.id.get}", api)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe(request)
      .flatMap(Unmarshal(_).to[publisher.models.API])
  }

  def postApiDetails(api: publisher.models.API,
                     token: OauthToken): Future[publisher.models.API] = {
    val request = (
      Post("/api/am/publisher/v0.10/apis", api)
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe(request)
      .flatMap(Unmarshal(_).to[publisher.models.API])
  }

  def apiChangeLifecycle(apiId: String,
                         action: ApiLifecycleAction,
                         token: OauthToken): Future[HttpResponse] = {
    val request = (
      Post(
        s"/api/am/publisher/v0.10/apis/change-lifecycle?apiId=$apiId&action=${action.toString}")
        ~> addHeader("Authorization", s"Bearer ${token.access_token}")
    )
    adminPipe(request)
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
    adminPipe(request)
      .flatMap(Unmarshal(_).to[NodeSeq])
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
