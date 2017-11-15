package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.tools.CommandLineToolVersion
import com.pacbio.secondary.smrtlink.jsonprotocols.ConfigModelsJsonProtocol
import com.pacbio.secondary.smrtlink.models.ConfigModels.Wso2Credentials
import com.pacbio.secondary.smrtlink.client.{
  ApiManagerAccessLayer,
  ApiManagerJsonProtocols,
  Retrying
}
import com.pacbio.secondary.smrtlink.client.Wso2Models._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.wso2.carbon.apimgt.rest.api.{publisher, store}
import scopt.OptionParser
import spray.json._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object AmClientModes {
  sealed trait Mode {
    val name: String
  }
  case object PROXY_ADMIN extends Mode { val name = "proxy-admin" }
  case object CREATE_ROLES extends Mode { val name = "create-roles" }
  case object GET_KEY extends Mode { val name = "get-key" }
  case object GET_ROLES_USERS extends Mode { val name = "get-roles-users" }
  case object SET_ROLES_USERS extends Mode { val name = "set-roles-users" }
  case object SET_API extends Mode { val name = "set-api" }
  case object UNKNOWN extends Mode { val name = "unknown" }
}

object loadFile extends (File => String) {
  def apply(file: File): String =
    FileUtils.readFileToString(file)

}

object loadResource extends (String => String) {
  def apply(resourcePath: String): String = {
    val path = (if (resourcePath.startsWith("/")) "" else "/") + resourcePath
    val stream = getClass.getResourceAsStream(path)
    io.Source.fromInputStream(stream).mkString
  }
}

object AmClientParser extends CommandLineToolVersion {

  val VERSION = "0.3.0"
  var TOOL_ID = "pbscala.tools.amclient"

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  def showVersion = showToolVersion(TOOL_ID, VERSION)

  // Examples:
  // smrt-server-analysis/target/pack/bin/amclient set-api --target http://localhost:8090/ --swagger-resource /smrtlink_swagger.json --app-config ~/p4/ui/main/apps/smrt-link/src/app-config.json --user admin --pass admin --host login14-biofx02 --port-offset 10

  val conf = ConfigFactory.load()

  val targetx = for {
    host <- Try { conf.getString("smrtflow.server.dnsName") }
    port <- Try { conf.getInt("smrtflow.server.port") }
  } yield new URL(s"http://$host:$port/")
  val target = targetx.toOption

  case class CustomConfig(
      mode: AmClientModes.Mode = AmClientModes.UNKNOWN,
      host: String = "localhost",
      portOffset: Int = 0,
      credsJson: Option[File] = None,
      user: String = null,
      pass: String = null,
      apiName: String = "SMRTLink",
      target: Option[URL] = target,
      roles: Seq[String] = List("Internal/PbAdmin",
                                "Internal/PbLabTech",
                                "Internal/PbBioinformatician"),
      scope: String = null,
      adminService: String = "RemoteUserStoreManagerService",
      swagger: Option[String] = None,
      // these Files are required in the commands that use them,
      // so they're not Options
      roleJson: File = null,
      appConfig: File = null,
      maxRetries: Int = 3,
      retryDelay: FiniteDuration = 5.seconds,
      defaultTimeOut: FiniteDuration = 30.seconds
  ) extends LoggerConfig {
    import ConfigModelsJsonProtocol._

    def validate(): CustomConfig = (user, pass, credsJson) match {
      case (null, null, None) =>
        copy(user = "admin", pass = "admin")
      case (_: String, _: String, None) =>
        this
      case (null, null, Some(f)) =>
        val creds = FileUtils
          .readFileToString(f, "UTF-8")
          .parseJson
          .convertTo[Wso2Credentials]
        copy(user = creds.wso2User, pass = creds.wso2Password)
      case _ =>
        throw new IllegalStateException("Failed to validate credentials")
    }
  }

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
      .action((x, c) => c.copy(pass = x))
      .text("API Manager admin password")

    opt[File]("creds-json")
      .action((x, c) => c.copy(credsJson = Some(x)))
      .text("Path to API Manager admin credentials json file")

    opt[Unit]("debug")
      .action(
        (_, c) =>
          c.asInstanceOf[LoggerConfig]
            .configure(c.logbackFile, c.logFile, true, c.logLevel)
            .asInstanceOf[CustomConfig])
      .text("Display debugging log output")

    checkConfig(c =>
      try {
        c.validate()
        success
      } catch {
        case e: IllegalStateException =>
          failure(
            "If you supply credentials, you must supply either a username and password or a credentials JSON file")
    })

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

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
          .text("Path to swagger json resource"),
        opt[Int]("max-retries")
          .action((m, c) => c.copy(maxRetries = m))
          .text(s"Max Number of Retries Default ${defaults.maxRetries}")
      )

    cmd(AmClientModes.CREATE_ROLES.name)
      .action((_, c) => c.copy(mode = AmClientModes.CREATE_ROLES))
      .text("create roles")
      .children(
        opt[Seq[String]]("roles")
          .action((roles, c) => c.copy(roles = roles))
          .text("list of roles"),
        opt[Int]("max-retries")
          .action((m, c) => c.copy(maxRetries = m))
          .text(s"Max Number of Retries Default ${defaults.maxRetries}")
      )

    cmd(AmClientModes.PROXY_ADMIN.name)
      .action((_, c) => c.copy(mode = AmClientModes.PROXY_ADMIN))
      .text("create a passthrough proxy for an admin service")
      .children(
        opt[String]("api-name")
          .action((a, c) => c.copy(apiName = a))
          .text("API Name"),
        opt[Seq[String]]("roles")
          .required()
          .action((roles, c) => c.copy(roles = roles))
          .text("list of roles"),
        opt[String]("scope")
          .required()
          .action((scope, c) => c.copy(scope = scope))
          .text("oauth scope for accessing admin api"),
        opt[String]("target")
          .action((x, c) => c.copy(target = Some(new URL(x))))
          .text("backend URL"),
        opt[File]("app-config")
          .required()
          .action((p, c) => c.copy(appConfig = p))
          .text("path to app-config.json file")
      )

    cmd(AmClientModes.GET_ROLES_USERS.name)
      .action((_, c) => c.copy(mode = AmClientModes.GET_ROLES_USERS))
      .text("get the users assigned to each of the given roles")
      .children(
        opt[Seq[String]]("roles")
          .action((roles, c) => c.copy(roles = roles))
          .text("list of roles"),
        opt[File]("role-json")
          .required()
          .action((roleJson, c) => c.copy(roleJson = roleJson))
          .text("json output file; will contain an object with <role>: [<list of user IDs>] mappings")
      )

    cmd(AmClientModes.SET_ROLES_USERS.name)
      .action((_, c) => c.copy(mode = AmClientModes.SET_ROLES_USERS))
      .text("recreate the given user/role mappings")
      .children(
        opt[File]("role-json")
          .required()
          .action((roleJson, c) => c.copy(roleJson = roleJson))
          .text("json input file; should contain an object with <role>: [<list of user IDs>] mappings"))

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"

    opt[Unit]("version") action { (x, c) =>
      println("")
      sys.exit(0)
    } text "Show tool version and exit"
  }
}

// two keys from the UI app config
case class ClientInfo(consumerKey: String, consumerSecret: String)

class AmClient(am: ApiManagerAccessLayer)(implicit actorSystem: ActorSystem)
    extends Retrying {

  import ApiManagerJsonProtocols._
  import actorSystem.dispatcher

  implicit val clientInfoFormat = jsonFormat2(ClientInfo)

  val scopes = Set("apim:subscribe",
                   "apim:api_create",
                   "apim:api_view",
                   "apim:api_publish")

  val reqTimeout = 30.seconds

  def createRoles(c: AmClientParser.CustomConfig): Future[String] = {
    for {
      existing <- am.getRoleNames(c.user, c.pass)
      toCreate = c.roles.toSet -- existing.toSet
      resultFuts = toCreate.map(r => am.addRole(c.user, c.pass, r))
      _ <- Future.sequence(resultFuts)
      msg <- Future.successful(s"added roles ${toCreate.mkString(", ")}")
    } yield msg
  }

  def createRolesWithRetry(c: AmClientParser.CustomConfig): Future[String] =
    retry(createRoles(c), c.retryDelay, c.maxRetries)(actorSystem.dispatcher,
                                                      actorSystem.scheduler)

  def updateAppConfigJson(appConfigFile: File,
                          app: store.models.Application): File = {

    app.keys.headOption match {
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
            Files.write(appConfigFile.toPath,
                        toSave.getBytes(StandardCharsets.UTF_8))
            appConfigFile
          }
          case _ => {
            throw new Exception("unexpected app config structure")
          }
        }
      }
      case None =>
        throw new Exception("unexpected app config structure")
    }

  }

  // get DefaultApplication key from the server and save it in appConfigFile
  def getKey(appConfigFile: File): Future[String] = {
    for {
      clientInfo <- am.register()
      tok <- am.login(clientInfo.clientId, clientInfo.clientSecret, scopes)
      appList <- am.searchApplications("DefaultApplication", tok)
      app <- Future.successful(appList.list.head)
      fullApp <- am.getApplication(app.applicationId.get, tok)
      _ <- Future.successful(updateAppConfigJson(appConfigFile, fullApp))
    } yield s"Successfully updated $appConfigFile"
  }

  def createApi(
      apiName: String,
      swagger: String,
      target: URL,
      endpointSecurity: Option[publisher.models.API_endpointSecurity] = None,
      token: OauthToken): Future[String] = {
    val swaggerJson = JsonParser(swagger).asJsObject
    val apiInfo = swaggerJson.getFields("info").head.asJsObject
    val description = apiInfo.getFields("description").headOption match {
      case Some(JsString(desc)) => Some(desc)
      case _ => None
    }
    val version = apiInfo.getFields("version").head match {
      case JsString(ver) => ver
      case _ =>
        throw new Exception(
          "swagger info " + apiInfo.toJson.compactPrint + " missing version")
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
      endpointSecurity = endpointSecurity,
      gatewayEnvironments = None,
      sequences = Some(List()),
      subscriptionAvailability = None,
      subscriptionAvailableTenants = Some(List()),
      businessInformation =
        Some(publisher.models.API_businessInformation(None, None, None, None)),
      corsConfiguration = Some(
        publisher.models.API_corsConfiguration(
          corsConfigurationEnabled = Some(false),
          accessControlAllowOrigins = Some(List("*")),
          accessControlAllowCredentials = Some(false),
          accessControlAllowHeaders = Some(
            List("authorization",
                 "Access-Control-Allow-Origin",
                 "Content-Type",
                 "SOAPAction")),
          accessControlAllowMethods =
            Some(List("GET", "PUT", "POST", "DELETE", "PATCH", "OPTIONS"))
        ))
    )

    for {
      created <- am.postApiDetails(api, token)
      pub <- am.apiChangeLifecycle(created.id.get,
                                   am.ApiLifecycleAction.PUBLISH,
                                   token)
      appList <- am.searchApplications("DefaultApplication", token)
      app = appList.list.head
      sub <- am.subscribe(created.id.get, app.applicationId.get, tier, token)
      msg <- Future.successful(s"created $apiName definition")
    } yield msg

  }

  // update target endpoints for the API with the given name
  def setApi(apiId: String,
             target: Option[URL],
             swagger: Option[String],
             endpointSecurity: Option[publisher.models.API_endpointSecurity] =
               None,
             token: OauthToken): Future[String] = {
    for {
      details <- am.getApiDetails(apiId, token)
      withEndpoints = setEndpoints(details, target)
      withSwagger = setSwagger(withEndpoints, swagger)
      withSecurity = withSwagger.copy(endpointSecurity = endpointSecurity)
      updated <- am.putApiDetails(withSecurity, token)
      msg <- Future.successful(s"updated API $apiId")
    } yield msg
  }

  def createOrUpdateApi(
      conf: AmClientParser.CustomConfig,
      endpointSecurity: Option[publisher.models.API_endpointSecurity] = None)
    : Future[String] = {

    def runCreateApi(token: OauthToken): Future[String] = {
      createApi(conf.apiName,
                conf.swagger.get,
                conf.target.get,
                endpointSecurity,
                token)
    }

    def toOrCreate(token: OauthToken,
                   apiList: publisher.models.APIList): Future[String] = {
      if (apiList.list.get.isEmpty) {
        runCreateApi(token)
      } else {
        val api = apiList.list.get.head
        setApi(api.id.get, conf.target, conf.swagger, endpointSecurity, token)
      }
    }

    for {
      clientInfo <- Future.successful(
        JsonParser(loadFile(conf.appConfig)).convertTo[ClientInfo])
      token <- am.login(clientInfo.consumerKey,
                        clientInfo.consumerSecret,
                        scopes)
      apiList <- am.searchApis(conf.apiName, token)
      msg <- toOrCreate(token, apiList)
    } yield msg
  }

  def createOrUpdateApiWithRetry(
      c: AmClientParser.CustomConfig,
      endpointSecurity: Option[publisher.models.API_endpointSecurity] = None) =
    retry(createOrUpdateApi(c, endpointSecurity), c.retryDelay, c.maxRetries)(
      actorSystem.dispatcher,
      actorSystem.scheduler)

  def setSwagger(details: publisher.models.API,
                 swaggerOpt: Option[String]): publisher.models.API =
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

  def setEndpoints(details: publisher.models.API,
                   targetOpt: Option[URL]): publisher.models.API =
    targetOpt
      .map(target => details.copy(endpointConfig = endpointConfig(target)))
      .getOrElse(details)

  def proxyAdmin(conf: AmClientParser.CustomConfig): Future[String] = {
    // API manager uses a generic swagger definition for SOAP endpoints
    val soapSwagger = s"""
{
  "paths": {
    "/*": {
      "post": {
        "parameters": [
          {
            "schema": {
              "type": "string"
            },
            "description": "SOAP request.",
            "name": "SOAP Request",
            "required": true,
            "in": "body"
          },
          {
            "description": "SOAPAction header for soap 1.1",
            "name": "SOAPAction",
            "type": "string",
            "required": false,
            "in": "header"
          }
        ],
        "responses": {
          "200": {
            "description": "OK"
          }
        },
        "x-auth-type": "Application & Application User",
        "x-throttling-tier": "Unlimited",
        "x-scope": "${conf.scope}"
      }
    }
  },
  "swagger": "2.0",
  "consumes": [
    "text/xml",
    "application/soap+xml"
  ],
  "produces": [
    "text/xml",
    "application/soap+xml"
  ],
  "info": {
    "title": "${conf.apiName}",
    "version": "1"
  },
  "x-wso2-security": {
    "apim": {
      "x-wso2-scopes": [
        {
          "name": "${conf.scope}",
          "description": "",
          "key": "${conf.scope}",
          "roles": "${conf.roles.mkString(",")}"
        }
      ]
    }
  }
}
    """

    val security = publisher.models.API_endpointSecurity(
      Some(publisher.models.API_endpointSecurityEnums.`Type`.Basic),
      Some(conf.user),
      Some(conf.pass))

    createOrUpdateApi(conf.copy(swagger = Some(soapSwagger)), Some(security))
  }

  private def writeRoles(jx: JsObject, output: Path): Path = {
    FileUtils.write(output.toFile, jx.toJson.prettyPrint)
    output
  }

  def getAndWriteRoles(c: AmClientParser.CustomConfig): Future[String] = {
    for {
      m <- Future.sequence(c.roles.map(r =>
        am.getUserListOfRole(c.user, c.pass, r).map(users => (r, users))))
      _ <- Future.successful(
        writeRoles(m.toMap.toJson.asJsObject, c.roleJson.toPath))
    } yield s"Successfully wrote roles to ${c.roleJson.toPath}"
  }

  def setRoles(c: AmClientParser.CustomConfig): Future[String] = {
    // Map of <Role> -> <list of users>
    val roleUsers = JsonParser(loadFile(c.roleJson))
      .convertTo[Map[String, Seq[String]]]

    val addNew = (role: String, users: Seq[String]) => {

      for {
        existing <- am.getUserListOfRole(c.user, c.pass, role)
        newUsers = (users.toSet -- existing.toSet).toList
        _ <- am.updateUserListOfRole(c.user, c.pass, role, newUsers)
      } yield s"Imported role $role"
    }

    Future.sequence(roleUsers.map(addNew.tupled)).map(_ => "Imported roles")
  }
}

object AmClient extends LazyLogging {

  // There's a lot of duplication and unnecessary blocking calls in AmClientParser
  // These should be removed and use the function within to run a
  // single call for each subparser command.
  def runAndBlock(fx: => Future[String],
                  timeOut: FiniteDuration,
                  prefixErrorMessage: Option[String]): Int = {
    Try(Await.result(fx, timeOut)) match {
      case Success(msg) => {
        println(msg)
        0
      }
      case Failure(err) => {
        val prefix = prefixErrorMessage.getOrElse("")
        val msg = s"$prefix$err"
        System.err.println(msg)
        logger.error(msg)
        1
      }
    }
  }

  def apply(conf: AmClientParser.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("amclient")

    val c = conf.validate()
    val am = new ApiManagerAccessLayer(c.host, c.portOffset, c.user, c.pass)
    val amClient = new AmClient(am)

    // This should be configurable from CLI
    val numWos2StartUpRetries = 60
    // Delay time between retries
    val wso2StartupRetryDelay = 10.seconds
    // Initial time to wait before submitting requests
    val initialWos2Delay = 10.seconds

    val totalStartupTimeOut
      : FiniteDuration = (numWos2StartUpRetries + 2) * wso2StartupRetryDelay

    // This error message might not be very useful. This might
    // need to be collapsed to a terse "WSO2 Did not start successfully" or similar
    def waitForWso2ToStartup(numRetires: Int): Future[String] =
      am.waitForStart(numRetires, initialWos2Delay)

    def runAndBlockWithDefault(fx: => Future[String],
                               prefixErrorMessage: Option[String]) =
      runAndBlock(fx, c.defaultTimeOut, prefixErrorMessage)

    def runMode(): Int = {
      c.mode match {
        case AmClientModes.CREATE_ROLES =>
          runAndBlockWithDefault(amClient.createOrUpdateApiWithRetry(c),
                                 Some("failed to add roles: "))
        case AmClientModes.GET_KEY =>
          runAndBlockWithDefault(
            amClient.getKey(c.appConfig),
            Some(s"Failed to Get Keys or Update ${c.appConfig}: "))
        case AmClientModes.GET_ROLES_USERS =>
          runAndBlockWithDefault(amClient.getAndWriteRoles(c),
                                 Some(s"Failed to Get User Roles: "))
        case AmClientModes.SET_ROLES_USERS =>
          runAndBlockWithDefault(amClient.setRoles(c),
                                 Some("Failed to Set Roles: "))
        case AmClientModes.SET_API =>
          runAndBlockWithDefault(amClient.createOrUpdateApiWithRetry(c),
                                 Some("Failed to set API: "))
        case AmClientModes.PROXY_ADMIN =>
          runAndBlockWithDefault(amClient.proxyAdmin(c),
                                 Some("Failed to Proxy Admin"))
        case x => {
          System.err.println(s"Unsupported action '$x'")
          1
        }
      }
    }

    // To get error messages propagated and avoid duplicated error/status messages, everything
    // must go through runAndBlock
    val rx: Try[Int] = for {
      _ <- Try(
        runAndBlock(waitForWso2ToStartup(numWos2StartUpRetries),
                    totalStartupTimeOut,
                    Some("Failed to Startup WSO2: ")))
      result <- Try(runMode())
    } yield result

    actorSystem.shutdown()

    rx match {
      case Success(n) => n
      case Failure(_) => 1
    }
  }
}

object AmClientApp extends App {
  def run(args: Seq[String]) = {
    val xc =
      AmClientParser.parser.parse(args.toSeq, AmClientParser.defaults) match {
        case Some(config) => AmClient(config)
        case _ => 1
      }
    sys.exit(xc)
  }
  run(args)
}
