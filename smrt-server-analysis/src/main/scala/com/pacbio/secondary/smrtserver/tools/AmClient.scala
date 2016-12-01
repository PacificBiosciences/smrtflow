package com.pacbio.secondary.smrtserver.tools

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.net.URL

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

import akka.actor.ActorSystem
import akka.util.Timeout

import spray.json._

import com.typesafe.config.ConfigFactory

import scopt.OptionParser

import com.pacbio.common.dependency.TypesafeSingletonReader
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

  val conf = ConfigFactory.load()

  val targetx = for {
    host <- Try { conf.getString("pb-services.host") }
    port <- Try { conf.getInt("pb-services.port")}
  } yield new URL(s"http://$host:$port/")
  val target = targetx.toOption

  case class CustomConfig(
    mode: AmClientModes.Mode = AmClientModes.UNKNOWN,
    host: String = "localhost",
    portOffset: Int = 0,
    user: String = "admin",
    pass: String = "admin",
    apiName: String = "SMRTLink",
    target: Option[URL] = target,
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

    cmd(AmClientModes.GET_KEY.name)
      .action((_, c) => c.copy(mode = AmClientModes.GET_KEY))
      .text("get the consumer key/secret from DefaultApplication and write it to the app-config file")
      .children(
        opt[File]("app-config")
          .required()
          .action((p, c) => c.copy(appConfig = p))
          .text("path to app-config.json file"))

    cmd(AmClientModes.CREATE_API.name)
      .action((_, c) => c.copy(mode = AmClientModes.CREATE_API))
      .text("create API from swagger")
      .children(
        opt[String]("api-name")
          .action((a, c) => c.copy(apiName = a))
          .text("API Name"),
        opt[String]("target")
          .action((x, c) => c.copy(target = Some(new URL(x))))
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

    cmd(AmClientModes.SET_API.name)
      .action((_, c) => c.copy(mode = AmClientModes.SET_API))
      .text("update backend target URL")
      .children(
        opt[String]("api-name")
          .action((a, c) => c.copy(apiName = a))
          .text("API Name"),
        opt[String]("target")
          .action((x, c) => c.copy(target = Some(new URL(x))))
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

    cmd(AmClientModes.CREATE_ROLES.name)
      .action((_, c) => c.copy(mode = AmClientModes.CREATE_ROLES))
      .text("create roles")
      .children(
        opt[String]("roles")
          .required()
          .action((roles, c) => c.copy(roles = roles))
          .text("list of roles"))

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"
  }
}

// two keys from the UI app config
case class ClientInfo(consumerKey: String, consumerSecret: String)


class AmClient(am: ApiManagerAccessLayer)(implicit actorSystem: ActorSystem) {

  import actorSystem.dispatcher

  import ApiManagerJsonProtocols._

  implicit val clientInfoFormat = jsonFormat2(ClientInfo)

  val scopes = Set("apim:subscribe", "apim:api_create", "apim:api_view", "apim:api_publish")

  val startupTimeout = 400.seconds

  def createRoles(roles: String): Int = {
    // TODO
    1
  }

  // get DefaultApplication key from the server and save it in appConfigFile
  def getKey(appConfigFile: File): Int = {
    Await.result(am.waitForStart(), startupTimeout)

    val futs = for {
      clientInfo <- am.register()
      tok <- am.login(clientInfo.clientId, clientInfo.clientSecret, scopes)
      appList <- am.searchApplications("DefaultApplication", tok)
      app = appList.list.head
      fullApp <- am.getApplication(app.applicationId.get, tok)
    } yield (clientInfo, tok, app, fullApp)
    val (clientInfo, tok, app, fullApp) = Await.result(futs, 30.seconds)

    fullApp.keys.headOption match {
      case Some(key) => {
        // leaving appConfigJson as json (and not converting to a case
        // class) because we only care about two keys in the app
        // config file, and creating a case class containing all the
        // structure from that file increases the coupling between
        // this code and the UI code.
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

  def createApi(apiName: String, appConfigFile: File, swagger: String, target: URL): Int = {
    val swaggerJson = JsonParser(swagger).asJsObject
    val apiInfo = swaggerJson.getFields("info").head.asJsObject
    val description = apiInfo.getFields("description").headOption match {
      case Some(JsString(desc)) => Some(desc)
      case _ => None
    }
    val version = apiInfo.getFields("version").head match {
      case JsString(ver) => ver
      case _ => throw new Exception("swagger info " + apiInfo.toJson.compactPrint + " missing version")
    }

    val tier = "Unlimited"

    val api = publisher.models.API(
      id = None,
      name = apiName,
      description = description,
      context = s"/${apiName}",
      version = version,
      provider = None,
      apiDefinition = swagger,
      wsdlUri = None,
      status = None,
      responseCaching = Some("Disabled"),
      cacheTimeout = None,
      destinationStatsEnabled = None,
      isDefaultVersion = true,
      transport = List("https"),
      tags = Some(List()),
      tiers = List(tier),
      maxTps = None,
      thumbnailUri = None,
      visibility = publisher.models.APIEnums.Visibility.PUBLIC,
      visibleRoles = Some(List()),
      visibleTenants = Some(List()),
      endpointConfig = endpointConfig(target),
      endpointSecurity = None,
      gatewayEnvironments = None,
      sequences = Some(List()),
      subscriptionAvailability = None,
      subscriptionAvailableTenants = Some(List()),
      businessInformation = Some(publisher.models.API_businessInformation(
        None, None, None, None)),
      corsConfiguration = Some(publisher.models.API_corsConfiguration(
        corsConfigurationEnabled = Some(false),
        accessControlAllowOrigins = Some(List("*")),
        accessControlAllowCredentials = Some(false),
        accessControlAllowHeaders = Some(List(
          "authorization",
          "Access-Control-Allow-Origin",
          "Content-Type",
          "SOAPAction")),
        accessControlAllowMethods = Some(List(
          "GET",
          "PUT",
          "POST",
          "DELETE",
          "PATCH",
          "OPTIONS")))))

    val clientInfo = JsonParser(loadFile(appConfigFile)).convertTo[ClientInfo]

    Await.result(am.waitForStart(), startupTimeout)

    val futs = for {
      token <- am.login(clientInfo.consumerKey, clientInfo.consumerSecret, scopes)
      created <- am.postApiDetails(api, token)
      pub <- am.apiChangeLifecycle(created.id.get, am.ApiLifecycleAction.PUBLISH, token)
      appList <- am.searchApplications("DefaultApplication", token)
      app = appList.list.head
      sub <- am.subscribe(created.id.get, app.applicationId.get, tier, token)
    } yield sub

    val sub = Await.result(futs, 30.seconds)

    0
  }

  // update target endpoints for the API with the given name
  def setApi(apiName: String, appConfigFile: File, target: Option[URL], swagger: Option[String]): Int = {
    val clientInfo = JsonParser(loadFile(appConfigFile)).convertTo[ClientInfo]

    Await.result(am.waitForStart(), startupTimeout)

    val futs = for {
      token <- am.login(clientInfo.consumerKey, clientInfo.consumerSecret, scopes)
      apiList <- am.searchApis(apiName, token)
      // Note, this assumes there's exactly one API with the given
      // name.  If we want to manage different versions of this API,
      // we'll have to do more work here.
      api = apiList.list.get.head
      details <- am.getApiDetails(api.id.get, token)
      withEndpoints = setEndpoints(details, target)
      withSwagger = setSwagger(withEndpoints, swagger)
      updated <- am.putApiDetails(withSwagger, token)
    } yield updated

    val updated = Await.result(futs, 30.seconds)

    0
  }

  def setSwagger(details: publisher.models.API, swaggerOpt: Option[String]): publisher.models.API =
    swaggerOpt
      .map(apiDef => details.copy(apiDefinition = apiDef))
      .getOrElse(details)

  def endpointConfig(target: URL): String = {
    s"""
{
  "production_endpoints": {
    "url": "${target}",
    "config": null
  },
  "sandbox_endpoints":{
    "url": "${target}",
    "config": null
  },
  "endpoint_type": "http"
}
"""
  }

  def setEndpoints(details: publisher.models.API, targetOpt: Option[URL]): publisher.models.API =
    targetOpt
      .map(target => details.copy(endpointConfig = endpointConfig(target)))
      .getOrElse(details)
}

object AmClient {
  def apply (c: AmClientParser.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("amclient")

    val am = new ApiManagerAccessLayer(c.host, c.portOffset, c.user, c.pass)
    val amClient = new AmClient(am)
    try {
      c.mode match {
        case AmClientModes.CREATE_API => amClient.createApi(c.apiName, c.appConfig, c.swagger.get, c.target.get)
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
