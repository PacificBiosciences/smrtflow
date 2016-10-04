package com.pacbio.secondary.smrtserver.tools

import java.net.URL
import java.io.{File, FileReader}
import java.nio.file.{Paths, Path}
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, X509TrustManager, TrustManager}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

import akka.actor.{ActorRef, ActorSystem}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import scopt.OptionParser

import spray.http._
import spray.can.Http
import spray.client.pipelining._
import spray.httpx
import spray.json._
import spray.httpx.SprayJsonSupport

import com.typesafe.scalalogging.LazyLogging

import com.pacbio.logging.{LoggerConfig, LoggerOptions}

import org.wso2.carbon.apimgt.rest.api.store


object AmClientModes {
  sealed trait Mode {
    val name: String
  }
  case object CREATE_API extends Mode {val name = "create-api"}
  case object CREATE_ROLES extends Mode {val name = "create-roles"}
  case object SET_KEY extends Mode {val name = "set-key"}
  case object UNKNOWN extends Mode {val name = "unknown"}
}

object AmClientParser {

  val VERSION = "0.1.0"
  var TOOL_ID = "pbscala.tools.amclient"

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  case class CustomConfig(
    mode: AmClientModes.Mode = AmClientModes.UNKNOWN,
    host: String = "localhost",
    portOffset: Int = 0,
    user: String = "admin",
    pass: String = "admin",
    target: String = "http://localhost:8081/",
    roles: String = "Internal/PbAdmin Internal/PbLabTech Internal/PbBioinformatics",
    swagger: Path = null,
    appConfig: File = null,
    command: CustomConfig => Unit = showDefaults
  ) extends LoggerConfig

  lazy val defaults = CustomConfig()

  lazy val parser = new OptionParser[CustomConfig]("amclient") {

    head("WSO2 API Manager Client", VERSION)

    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of API Manager server"

    opt[Int]("port-offset") action { (x, c) =>
      c.copy(portOffset = x)
    } text "API Manager port offset"

    opt[String]("user") action { (x, c) =>
      c.copy(user = x)
    } text "API Manager admin username"

    opt[String]("pass") action { (x, c) =>
      c.copy(pass = x)
    } text "API Manager admin password"

    opt[Unit]("debug") action { (_, c) =>
      c.asInstanceOf[LoggerConfig].configure(c.logbackFile, c.logFile, true, c.logLevel).asInstanceOf[CustomConfig]
    } text "Display debugging log output"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

    cmd(AmClientModes.CREATE_API.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = AmClientModes.CREATE_API)
    } children(
      opt[String]("target") action { (x, c) =>
        c.copy(target = x)
      } text "backend URL",

      opt[File]("swagger") action { (p, c) =>
        c.copy(swagger = p.toPath)
      } text "Path to swagger json file"
    ) text "create API from swagger"

    cmd(AmClientModes.CREATE_ROLES.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = AmClientModes.CREATE_ROLES)
    } children(
      opt[String]("roles") action { (roles, c) =>
        c.copy(roles = roles)
      } text "list of roles"
    ) text "create roles"

    cmd(AmClientModes.SET_KEY.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = AmClientModes.SET_KEY)
    } children(
      arg[File]("app-config") action { (p, c) =>
        c.copy(appConfig = p)
      } text "path to app-config.json file"
    ) text "take consumer key/secret from app-config.json and add it to DefaultApplication"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"
  }
}

case class ClientRegistrationRequest(callbackUrl: String, clientName: String, tokenScope: String = "Production", owner: String, grantType: String = "password refresh_token", saasApp: Boolean)

case class ClientRegistrationResponse(appOwner: Option[String], clientName: Option[String], callBackURL: String, isSaasApplication: Boolean, jsonString: String, clientId: String, clientSecret: String)

case class OauthToken(access_token: String, refresh_token: String, scope: String, token_type: String, expires_in: Int)

case class AppConfig(consumerKey: String, consumerSecret: String)

object AmClientJsonProtocols extends DefaultJsonProtocol {
  implicit val clientRegistrationRequestFormat = jsonFormat6(ClientRegistrationRequest)
  implicit val clientRegistrationResponseFormat = jsonFormat7(ClientRegistrationResponse)
  implicit val oauthTokenFormat = jsonFormat5(OauthToken)
  implicit val appConfigFormat = jsonFormat2(AppConfig)

  implicit val applicationKeyEnumFormat = new EnumJsonFormat(store.models.ApplicationKeyEnums.KeyType)
  implicit val applicationKeyGenerateRequestEnumFormat = new EnumJsonFormat(store.models.ApplicationKeyGenerateRequestEnums.KeyType)
  implicit val documentEnumTypeFormat = new EnumJsonFormat(store.models.DocumentEnums.`Type`)
  implicit val documentEnumSourceTypeFormat = new EnumJsonFormat(store.models.DocumentEnums.SourceType)
  implicit val subscriptionEnumFormat = new EnumJsonFormat(store.models.SubscriptionEnums.Status)
  implicit val tierLevelForomat = new EnumJsonFormat(store.models.TierEnums.TierLevel)
  implicit val tierEnumFormat = new EnumJsonFormat(store.models.TierEnums.TierPlan)

  implicit val tokenFormat = jsonFormat3(store.models.Token)
  implicit val applicationInfoFormat = jsonFormat7(store.models.ApplicationInfo)
  implicit val applicationListFormat = jsonFormat4(store.models.ApplicationList)
  implicit val applicationKeyFormat = jsonFormat6(store.models.ApplicationKey)
  implicit val applicationFormat = jsonFormat9(store.models.Application)
}

class AmClient(host: String, portOffset: Int = 0, user: String, pass: String)(implicit actorSystem: ActorSystem) extends LazyLogging {

  import actorSystem.dispatcher

  import AmClientJsonProtocols._
  import SprayJsonSupport._

  implicit val timeout: Timeout = 30.seconds

  val ADMIN_PORT = 9443
  val API_PORT = 8243

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

  val adminPipe: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <-
        IO(Http) ? Http.HostConnectorSetup(host, port = ADMIN_PORT + portOffset, sslEncryption = true)
    ) yield sendReceive(connector)

  val apiPipe: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <-
        IO(Http) ? Http.HostConnectorSetup(host, port = API_PORT + portOffset, sslEncryption = true)
    ) yield sendReceive(connector)

  def createApi(swagger: Path, target: String): Int = {
    // TODO
    1
  }

  def createRoles(roles: String): Int = {
    // TODO
    1
  }

  def setKey(appConfigFile: File): Int = {
    val appConfigContents = io.Source.fromFile(appConfigFile).mkString
    val appConfig = JsonParser(appConfigContents).convertTo[AppConfig]

    val futs = for {
      ci <- register()
      tok <- login(ci)
      appList <- searchApplications("DefaultApplication", tok)
      app = appList.list.head
      fullApp <- getApplication(app.applicationId.get, tok)
    } yield (ci, tok, app, fullApp)
    val (ci, tok, app, fullApp) = Await.result(futs, 10.seconds)
    println(ci)
    println(tok)
    println(app)
    println(fullApp)

    val matchingKeys = fullApp.keys.filter(_.consumerKey == appConfig.consumerKey)
    if (matchingKeys.length > 0) {
      println("Application already has consumer key")
      0
    } else {
      val key = store.models.ApplicationKey(appConfig.consumerKey, appConfig.consumerSecret, None, "COMPLETED", store.models.ApplicationKeyEnums.KeyType.PRODUCTION, None)
      val newApp = fullApp.copy(keys = fullApp.keys ++ List(key))
      println(newApp)
      val resp = Await.result(updateApplication(newApp, tok), 10.seconds)
      println(resp)
      0
    }
  }

    def updateApplication(app: store.models.Application, token: OauthToken): Future[HttpResponse] = {
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

  def login(clientInfo: ClientRegistrationResponse): Future[OauthToken] = {
    val body = FormData(Map(
      "grant_type" -> "password",
      "username" -> user,
      "password" -> pass,
      "scope" -> "apim:subscribe"
    ))

    val request = (
      Post(s"/token", body)
        ~> addCredentials(BasicHttpCredentials(clientInfo.clientId, clientInfo.clientSecret))
    )

    apiPipe.flatMap(_(request)).map(unmarshal[OauthToken])
  }

  def register(): Future[ClientRegistrationResponse] = {
    val body = ClientRegistrationRequest("foo", Random.alphanumeric.take(20).mkString, "Production", user, "password refresh_token", true)

    val request = (
      Post(s"/client-registration/v0.10/register", body)
        ~> addCredentials(BasicHttpCredentials(user, pass))
    )

    adminPipe.flatMap(_(request)).map(unmarshal[ClientRegistrationResponse])
  }
}

object AmClient {
  def apply (c: AmClientParser.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("amclient")

    val amcl = new AmClient(c.host, c.portOffset, c.user, c.pass)
    try {
      c.mode match {
        case AmClientModes.CREATE_API => amcl.createApi(c.swagger, c.target)
        case AmClientModes.CREATE_ROLES => amcl.createRoles(c.roles)
        case AmClientModes.SET_KEY => amcl.setKey(c.appConfig)
        case _ => {
          println("Unsupported action")
          1
        }
      }
    } finally {
      actorSystem.shutdown()
    }
  }
}

object AmClientApp extends App {
  def run(args: Seq[String]) = {
    val xc = AmClientParser.parser.parse(args.toSeq, AmClientParser.defaults) match {
      case Some(config) => AmClient(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
