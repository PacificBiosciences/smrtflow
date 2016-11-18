package com.pacbio.secondary.smrtserver.tools

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import akka.actor.ActorSystem

import spray.json._

import scopt.OptionParser

import com.pacbio.logging.{LoggerConfig, LoggerOptions}

import org.wso2.carbon.apimgt.rest.api.publisher


object AmClientModes {
  sealed trait Mode {
    val name: String
  }
  case object CREATE_API extends Mode {val name = "create-api"}
  case object CREATE_ROLES extends Mode {val name = "create-roles"}
  case object GET_KEY extends Mode {val name = "get-key"}
  case object SET_API extends Mode {val name = "set-api"}
  case object UNKNOWN extends Mode {val name = "unknown"}
}

object loadFile extends (File => String) {
  def apply(file: File): String = {
    val acSource = scala.io.Source.fromFile(file)
    try acSource.mkString finally acSource.close()
  }
}

object loadResource extends (String => String) {
  def apply(resourcePath: String): String = {
    val stream = getClass.getResourceAsStream(resourcePath)
    io.Source.fromInputStream(stream).mkString
  }
}

object AmClientParser {

  val VERSION = "0.1.0"
  var TOOL_ID = "pbscala.tools.amclient"

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  // Examples:
  // smrt-server-analysis/target/pack/bin/amclient set-api --target http://localhost:8090/ --swagger-resource /smrtlink_swagger.json --app-config ~/p4/ui/main/apps/smrt-link/src/app-config.json --user admin --pass admin --host login14-biofx02 --port-offset 10

  case class CustomConfig(
    mode: AmClientModes.Mode = AmClientModes.UNKNOWN,
    host: String = "localhost",
    portOffset: Int = 0,
    user: String = "admin",
    pass: String = "admin",
    apiName: String = "SMRTLink",
    target: Option[String] = None,
    roles: String = "Internal/PbAdmin Internal/PbLabTech Internal/PbBioinformatics",
    swagger: Option[String] = None,
    // appConfig is required in the commands that use it, so it's not
    // an Option
    appConfig: File = null
  ) extends LoggerConfig

  lazy val defaults = CustomConfig()

  lazy val parser = new OptionParser[CustomConfig]("amclient") {

    head("WSO2 API Manager Client", VERSION)

    opt[String]("host")
      .action((x, c) => c.copy(host = x))
      .text("Hostname of API Manager server")

    opt[Int]("port-offset")
      .action((x, c) => c.copy(portOffset = x))
      .text("API Manager port offset")

    opt[String]("user")
      .action((x, c) => c.copy(user = x))
      .text("API Manager admin username")

    opt[String]("pass")
      .action((x, c) => c.copy(pass = x) )
      .text("API Manager admin password")


    opt[Unit]("debug")
      .action((_, c) => c.asInstanceOf[LoggerConfig].configure(c.logbackFile, c.logFile, true, c.logLevel).asInstanceOf[CustomConfig])
      .text("Display debugging log output")

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])


    cmd(AmClientModes.CREATE_API.name)
      .action((_, c) => c.copy(mode = AmClientModes.CREATE_API))
      .text("create API from swagger")
      .children(
        opt[String]("target")
          .required()
          .action((x, c) => c.copy(target = Some(x)))
          .text("backend URL"),
        opt[File]("swagger-file")
          .action((f, c) => c.copy(swagger = Some(loadFile(f))))
          .text("Path to swagger json file"),
        opt[String]("swagger-resource")
          .action((p, c) => c.copy(swagger = Some(loadResource(p))))
          .text("Path to swagger json resource"))

    cmd(AmClientModes.CREATE_ROLES.name)
      .action((_, c) => c.copy(mode = AmClientModes.CREATE_ROLES))
      .text("create roles")
      .children(
        opt[String]("roles")
          .required()
          .action((roles, c) => c.copy(roles = roles))
          .text("list of roles"))

    cmd(AmClientModes.GET_KEY.name)
      .action((_, c) => c.copy(mode = AmClientModes.GET_KEY))
      .text("get the consumer key/secret from DefaultApplication and write it to the app-config file")
      .children(
        opt[File]("app-config")
          .required()
          .action((p, c) => c.copy(appConfig = p))
          .text("path to app-config.json file"))

    cmd(AmClientModes.SET_API.name)
      .action((_, c) => c.copy(mode = AmClientModes.SET_API))
      .text("update backend target URL")
      .children(
        opt[String]("api-name")
          .action((a, c) => c.copy(apiName = a))
          .text("API Name"),
        opt[String]("target")
          .action((x, c) => c.copy(target = Some(x)))
          .text("backend URL"),
        opt[File]("app-config")
          .required()
          .action((p, c) => c.copy(appConfig = p))
          .text("path to app-config.json file"),
        opt[File]("swagger-file")
          .action((f, c) => c.copy(swagger = Some(loadFile(f))))
          .text("Path to swagger json file"),
        opt[String]("swagger-resource")
          .action((p, c) => c.copy(swagger = Some(loadResource(p))))
          .text("Path to swagger json resource"))

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

  def createApi(swagger: String, target: String): Int = {
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

    fullApp.keys.headOption match {
      case Some(key) => {
        val appConfigJson = JsonParser(loadFile(appConfigFile))

        val newAttribs = Map(
          "consumerKey" -> JsString(key.consumerKey),
          "consumerSecret" -> JsString(key.consumerSecret)
        )

        appConfigJson match {
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
      }
      case None => 1
    }
  }

  // update target endpoints for the API with the given name
  def setApi(apiName: String, appConfigFile: File, target: Option[String], swagger: Option[String]): Int = {
    val appConfig = JsonParser(loadFile(appConfigFile)).convertTo[AppConfig]

    val futs = for {
      token <- am.login(appConfig.consumerKey, appConfig.consumerSecret, scopes)
      apiList <- am.searchApis(apiName, token)
      // Note, this assumes there's exactly one API with the given
      // name.  If we want to manage different versions of this API,
      // we'll have to do more work here.
      api = apiList.list.get.head
      details <- am.getApiDetails(api.id.get, token)
      withEndpoints = setEndpoints(details, target)
      withSwagger = setSwagger(withEndpoints, swagger)
      updated <- am.putApiDetails(withSwagger, token)
    } yield (updated)

    val (updated) = Await.result(futs, 10.seconds)

    0
  }

  def setSwagger(details: publisher.models.API, swaggerOpt: Option[String]): publisher.models.API = {
    swaggerOpt match {
      case Some(swagger) => {
        details.copy(apiDefinition = swagger)
      }
      case None => details
    }
  }

  def setEndpoints(details: publisher.models.API, targetOpt: Option[String]): publisher.models.API = {
    targetOpt match {
      case Some(target) => {
        val newEndpointConfig = s"""
{
  "production_endpoints": {
    "url": "${target}",
    "config": null
  },
  "sandbox_endpoints\":{
    "url": "${target}",
    "config": null
  },
  "endpoint_type": "http"
}
"""

        details.copy(endpointConfig = newEndpointConfig)
      }
      case None => details
    }
  }
}

object AmClient {
  def apply (c: AmClientParser.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("amclient")

    val am = new ApiManagerAccessLayer(c.host, c.portOffset, c.user, c.pass)
    val amClient = new AmClient(am)
    try {
      c.mode match {
        case AmClientModes.CREATE_API => amClient.createApi(c.swagger.get, c.target.get)
        case AmClientModes.CREATE_ROLES => amClient.createRoles(c.roles)
        case AmClientModes.GET_KEY => amClient.getKey(c.appConfig)
        case AmClientModes.SET_API => amClient.setApi(c.apiName, c.appConfig, c.target, c.swagger)
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
