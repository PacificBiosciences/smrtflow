package com.pacbio.secondary.smrtserver.tools

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.ActorSystem

import spray.json._

import scopt.OptionParser

import com.pacbio.logging.{LoggerConfig, LoggerOptions}


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

  // Examples:
  // smrt-server-analysis/target/pack/bin/amclient set-endpoint --target http://localhost:8081/ --app-config ~/p4/ui/main/apps/smrt-link/src/app-config.json --user admin --pass admin --host login14-biofx02 --port-offset 10

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

// the actual app config class
case class AppConfig(consumerKey: String, consumerSecret: String)


class AmClient(am: ApiManagerAccessLayer)(implicit actorSystem: ActorSystem) {

  import actorSystem.dispatcher

  import ApiManagerJsonProtocols._

  implicit val appConfigFormat = jsonFormat2(AppConfig)

  val scopes = "apim:subscribe apim:api_create apim:api_view"

  def createApi(swagger: Path, target: String): Int = {
    // TODO
    1
  }

  def createRoles(roles: String): Int = {
    // TODO
    1
  }

  // get DefaultApplication key from the server and save it in appConfigFile
  def getKey(appConfigFile: File): Int = {
    val futs = for {
      clientInfo <- am.register()
      tok <- am.login(clientInfo.clientId, clientInfo.clientSecret, scopes)
      appList <- am.searchApplications("DefaultApplication", tok)
      app = appList.list.head
      fullApp <- am.getApplication(app.applicationId.get, tok)
    } yield (clientInfo, tok, app, fullApp)
    val (clientInfo, tok, app, fullApp) = Await.result(futs, 10.seconds)

    if (fullApp.keys.length > 0) {
      val appConfigContents = io.Source.fromFile(appConfigFile).mkString
      val appConfig = JsonParser(appConfigContents)

      val key = fullApp.keys.head
      val newAttribs = Map(
        "consumerKey" -> JsString(key.consumerKey),
        "consumerSecret" -> JsString(key.consumerSecret)
      )

      appConfig match {
        case JsObject(fields) => {
          val toSave = JsObject(fields ++ newAttribs).prettyPrint
          Files.write(appConfigFile.toPath, toSave.getBytes(StandardCharsets.UTF_8))
          0
        }
        case _ => {
          System.err.println("unexpected app config structure")
          1
        }
      }
    } else {
      1
    }
  }

  // update target endpoints for the API with the given name
  def setEndpoint(apiName: String, appConfigFile: File, target: String): Int = {
    val appConfigContents = io.Source.fromFile(appConfigFile).mkString
    val appConfig = JsonParser(appConfigContents).convertTo[AppConfig]

    val futs = for {
      token <- am.login(appConfig.consumerKey, appConfig.consumerSecret, scopes)
      apiList <- am.searchApis(apiName, token)
      // Note, this assumes there's exactly one API with the given
      // name.  If we want to manage different versions of this API,
      // we'll have to do more work here.
      api = apiList.list.get.head
      updated <- am.updateEndpoints(api, target, target, token)
    } yield (token, apiList, api, updated)

    val (token, apiList, api, fullApi) = Await.result(futs, 10.seconds)

    0
  }
}

object AmClient {
  def apply (c: AmClientParser.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("amclient")

    val am = new ApiManagerAccessLayer(c.host, c.portOffset, c.user, c.pass)
    val amClient = new AmClient(am)
    try {
      c.mode match {
        case AmClientModes.CREATE_API => amClient.createApi(c.swagger, c.target)
        case AmClientModes.CREATE_ROLES => amClient.createRoles(c.roles)
        case AmClientModes.GET_KEY => amClient.getKey(c.appConfig)
        case AmClientModes.SET_ENDPOINT => amClient.setEndpoint(c.apiName, c.appConfig, c.target)
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
