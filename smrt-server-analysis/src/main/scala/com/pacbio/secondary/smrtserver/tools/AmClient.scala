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
import spray.httpx.marshalling.BasicMarshallers._
import spray.json._
import spray.httpx.SprayJsonSupport

import com.typesafe.scalalogging.LazyLogging

import com.pacbio.logging.{LoggerConfig, LoggerOptions}

import org.wso2.carbon.apimgt.rest.api.store
import org.wso2.carbon.apimgt.rest.api.publisher


object AmClientModes {
  sealed trait Mode {
    val name: String
  }
  case object CREATE_API extends Mode {val name = "create-api"}
  case object CREATE_ROLES extends Mode {val name = "create-roles"}
  case object GET_KEY extends Mode {val name = "get-key"}
  case object SET_ENDPOINT extends Mode {val name = "set-endpoint"}
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
    apiName: String = "SMRTLink",
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

    cmd(AmClientModes.GET_KEY.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = AmClientModes.GET_KEY)
    } children(
      opt[File]("app-config") action { (p, c) =>
        c.copy(appConfig = p)
      } text "path to app-config.json file"
    ) text "get the consumer key/secret from DefaultApplication and write it to the app-config file"

    cmd(AmClientModes.SET_ENDPOINT.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = AmClientModes.SET_ENDPOINT)
    } children(
      opt[String]("api-name") action { (a, c) =>
        c.copy(apiName = a)
      } text "API Name",
      opt[String]("target") action { (x, c) =>
        c.copy(target = x)
      } text "backend URL",
      opt[File]("app-config") action { (p, c) =>
        c.copy(appConfig = p)
      } text "path to app-config.json file"
    ) text "update backend target URL"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"
  }
}


case class AppConfig(consumerKey: String, consumerSecret: String)


class AmClient(host: String, portOffset: Int = 0, user: String, pass: String)(implicit actorSystem: ActorSystem) extends LazyLogging {

  import actorSystem.dispatcher

  import ApiManagerJsonProtocols._
  import SprayJsonSupport._

  implicit val timeout: Timeout = 30.seconds

  val ADMIN_PORT = 9443
  val API_PORT = 8243

  implicit val appConfigFormat = jsonFormat2(AppConfig)

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

  def getKey(appConfigFile: File): Int = {
    val futs = for {
      clientInfo <- register()
      tok <- login(clientInfo.clientId, clientInfo.clientSecret)
      appList <- searchApplications("DefaultApplication", tok)
      app = appList.list.head
      fullApp <- getApplication(app.applicationId.get, tok)
    } yield (clientInfo, tok, app, fullApp)
    val (clientInfo, tok, app, fullApp) = Await.result(futs, 10.seconds)

    if (fullApp.keys.length > 0) {
      // TODO: write consumer key/secret to appConfigFile
      0
    } else {
      1
    }
  }

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

  def login(consumerKey: String, consumerSecret: String): Future[OauthToken] = {
    val body = FormData(Map(
      "grant_type" -> "password",
      "username" -> user,
      "password" -> pass,
      "scope" -> "apim:subscribe apim:api_create apim:api_view"
    ))

    val request = (
      Post(s"/token", body)
        ~> addCredentials(BasicHttpCredentials(consumerKey, consumerSecret))
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

  def updateEndpoints(api: publisher.models.APIInfo, prodEndpoint: String, devEndpoint: String, token: OauthToken): Future[publisher.models.API] = {
    val newEndpointConfig = s"""
{
  "production_endpoints": {
    "url": "${prodEndpoint}",
    "config": null
  },
  "sandbox_endpoints\":{
    "url": "${devEndpoint}",
    "config": null
  },
  "endpoint_type": "http"
}
"""

    getApiDetails(api.id.get, token).flatMap({ details =>
      val updated = details.copy(endpointConfig = newEndpointConfig)
      putApiDetails(updated, token)
    })
  }

  def setEndpoint(apiName: String, appConfigFile: File, target: String): Int = {
    val appConfigContents = io.Source.fromFile(appConfigFile).mkString
    val appConfig = JsonParser(appConfigContents).convertTo[AppConfig]

    val futs = for {
      token <- login(appConfig.consumerKey, appConfig.consumerSecret)
      apiList <- searchApis(apiName, token)
      // Note, this assumes there's exactly one API with the given
      // name.  If we want to manage different versions of this API,
      // we'll have to do more work here.
      api = apiList.list.get.head
      updated <- updateEndpoints(api, target, target, token)
    } yield (token, apiList, api, updated)

    val (token, apiList, api, fullApi) = Await.result(futs, 10.seconds)

    0
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
        case AmClientModes.GET_KEY => amcl.getKey(c.appConfig)
        case AmClientModes.SET_ENDPOINT => amcl.setEndpoint(c.apiName, c.appConfig, c.target)
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
