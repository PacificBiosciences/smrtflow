package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.tools.{
  CommandLineToolVersion,
  timeUtils
}
import com.pacbio.secondary.smrtlink.jsonprotocols.ConfigModelsJsonProtocol
import com.pacbio.secondary.smrtlink.models.ConfigModels.Wso2Credentials
import com.pacbio.secondary.smrtlink.client.{
  ApiManagerAccessLayer,
  ApiManagerJsonProtocols,
  Retrying,
  Wso2Models
}
import com.pacbio.secondary.smrtlink.client.Wso2Models._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.wso2.carbon.apimgt.rest.api.{publisher, store}
import scopt.OptionParser
import spray.json._
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object AmClientModes {
  sealed trait Mode {
    val name: String
  }
  case object STATUS extends Mode { val name = "get-status" }
  case object PROXY_ADMIN extends Mode { val name = "proxy-admin" }
  case object CREATE_ROLES extends Mode { val name = "create-roles" }
  case object CREATE_USER extends Mode { val name = "create-user" }
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
    scala.io.Source.fromInputStream(stream).mkString
  }
}

/**
  * Many methods in here should be pushed back into the AmClient to make this abstraction
  * more well formed and clearly delinated from the ApiManagerAccessLayer.
  *
  * This should extend or augment methods in ApiManagerAccessLayer that have to do with
  * writing bundle specific files (e.g., app-config.json).
  *
  * FIXME(mpkocher)(11-15-2017) The "getStatus" call in here should be used in GetSystemStatusTool
  *
  * These are currently doing two different things. Mostly because these abstractions are
  * not extending any base trait or interface.
  *
  */
object AmClientParser extends CommandLineToolVersion {

  val VERSION = "0.3.3"
  var TOOL_ID = "pbscala.tools.amclient"

  def showDefaults(c: AmClientOptions): Unit = {
    println(s"Defaults $c")
  }

  def showVersion = showToolVersion(TOOL_ID, VERSION)

  // Examples:
  // smrt-server-analysis/target/pack/bin/amclient set-api --target http://localhost:8090/ --swagger-resource /smrtlink_swagger.json --app-config ~/p4/ui/main/apps/smrt-link/src/app-config.json --user admin --pass admin --host login14-biofx02 --port-offset 10

  val conf = ConfigFactory.load()

  val targetx = for {
    host <- Try { conf.getString("smrtflow.server.host") }
    port <- Try { conf.getInt("smrtflow.server.port") }
  } yield new URL(s"http://$host:$port/")
  val target = targetx.toOption

  case class AmClientOptions(
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
                                "Internal/PbBioinformatician",
                                "Internal/PbInstrument"),
      scope: String = null,
      adminService: String = "RemoteUserStoreManagerService",
      swagger: Option[String] = None,
      // these Files are required in the commands that use them,
      // so they're not Options
      roleJson: File = null,
      maxRetries: Int = 3,
      retryDelay: FiniteDuration = 5.seconds,
      defaultTimeOut: FiniteDuration = 30.seconds,
      w2StartUpRetries: Int = 60,
      w2StartUpRetryDelay: FiniteDuration = 10.seconds,
      newUser: Option[String] = None,
      newPassword: Option[String] = None,
      newUserJson: Option[File] = None
  ) extends LoggerConfig {
    import ConfigModelsJsonProtocol._

    private def toWso2Credentials(f: File) =
      FileUtils
        .readFileToString(f, "UTF-8")
        .parseJson
        .convertTo[Wso2Credentials]

    def validate(): AmClientOptions = (user, pass, credsJson) match {
      case (null, null, None) =>
        copy(user = "admin", pass = "admin")
      case (_: String, _: String, None) =>
        this
      case (null, null, Some(f)) =>
        val creds = toWso2Credentials(f)
        copy(user = creds.wso2User, pass = creds.wso2Password)
      case _ =>
        throw new IllegalStateException("Failed to validate credentials")
    }

    def getNewUserCreds: Wso2Credentials =
      (newUser, newPassword, newUserJson) match {
        case (None, None, Some(f)) =>
          toWso2Credentials(f)
        case (Some(user), Some(password), None) =>
          Wso2Credentials(user, password)
        case _ =>
          throw new IllegalStateException(
            "Either --new-user-creds OR --new-user and --new-password is required")
      }
  }

  lazy val defaults = AmClientOptions()

  lazy val parser = new OptionParser[AmClientOptions]("amclient") {

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
            .asInstanceOf[AmClientOptions])
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

    cmd(AmClientModes.STATUS.name)
      .action((_, c) => c.copy(mode = AmClientModes.STATUS))
      .text("Get Status of WSO2")
      .children(
        opt[Int]("max-retries")
          .action((m, c) => c.copy(w2StartUpRetries = m))
          .text(
            s"Max Number of WSO2 Retries (Default ${defaults.w2StartUpRetries})"),
        opt[Int]("retry-delay")
          .action((m, c) => c.copy(w2StartUpRetryDelay = m.seconds))
          .text(
            s"Time in sec between WSO2 Retries (Default ${defaults.w2StartUpRetryDelay})")
      )

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
          .text("backend URL")
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

    cmd(AmClientModes.CREATE_USER.name)
      .action((_, c) => c.copy(mode = AmClientModes.CREATE_USER))
      .text("Add a user to WSO2")
      .children(
        opt[String]("new-user")
          .action((newUser, c) => c.copy(newUser = Some(newUser)))
          .text("Login ID of new user"),
        opt[String]("new-password")
          .action((newPassword, c) => c.copy(newPassword = Some(newPassword)))
          .text("Password of new user"),
        opt[File]("new-user-creds-json")
          .action((x, c) => c.copy(newUserJson = Some(x)))
          .text("Path to new user credentials json file"),
        opt[String]("role")
          .required()
          .action((newRole, c) => c.copy(roles = Seq(newRole)))
          .text("Role of new user")
      )

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"

    opt[Unit]("version") action { (x, c) =>
      println("")
      sys.exit(0)
    } text "Show tool version and exit"

    // Don't show the help if validation error
    override def showUsageOnError = false
  }

}

// two keys from the UI app config
case class ClientInfo(consumerKey: String, consumerSecret: String)

class AmClient(am: ApiManagerAccessLayer)(implicit actorSystem: ActorSystem)
    extends Retrying
    with LazyLogging {

  import ApiManagerJsonProtocols._
  // This will be used as the execution context
  import actorSystem.dispatcher

  implicit val clientInfoFormat = jsonFormat2(ClientInfo)

  val scopes = Set("apim:subscribe",
                   "apim:api_create",
                   "apim:api_view",
                   "apim:api_publish")

  def createRoles(c: AmClientParser.AmClientOptions): Future[String] = {
    for {
      existing <- am.getRoleNames(c.user, c.pass)
      toCreate <- Future.successful(c.roles.toSet -- existing.toSet)
      _ <- Future.sequence(toCreate.map(r => am.addRole(c.user, c.pass, r)))
      msg <- Future.successful(
        s"added roles ${toCreate.mkString(", ")} to user ${c.user}")
    } yield msg
  }

  def createRolesWithRetry(c: AmClientParser.AmClientOptions): Future[String] =
    retry(createRoles(c), c.retryDelay, c.maxRetries)(actorSystem.dispatcher,
                                                      actorSystem.scheduler)

  def createUser(c: AmClientParser.AmClientOptions): Future[String] = {
    val creds = c.getNewUserCreds

    def toResult(x: Boolean) =
      if (x) {
        Future.successful(
          s"Added user ${creds.wso2User} with role ${c.roles.head}")
      } else {
        Future.failed(
          new RuntimeException(s"Couldn't add user ${creds.wso2User}"))
      }

    def addUser(isExisting: Boolean) =
      if (isExisting) {
        Future.successful(s"User '${creds.wso2User}' already exists")
      } else {
        am.addUser(c.user,
                   c.pass,
                   creds.wso2User,
                   creds.wso2Password,
                   c.roles.head)
          .flatMap(toResult)
      }

    for {
      isExisting <- am.isExistingUser(c.user, c.pass, creds.wso2User)
      userResponse <- addUser(isExisting)
    } yield userResponse.toString
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
      msg <- Future.successful(s"updated WSO2 API $apiId")
    } yield msg
  }

  def andLog(sx: String): Future[String] = Future {
    logger.info(sx)
    sx
  }

  def createOrUpdateApi(
      conf: AmClientParser.AmClientOptions,
      clientReg: ClientRegistrationResponse,
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
      _ <- andLog("Starting to Create or Update API")
      token <- am.login(clientReg.clientId, clientReg.clientSecret, scopes)
      apiList <- am.searchApis(conf.apiName, token)
      msg <- toOrCreate(token, apiList)
      _ <- andLog(msg)
    } yield msg
  }

  def createOrUpdateApiWithRetry(
      c: AmClientParser.AmClientOptions,
      clientReg: ClientRegistrationResponse,
      endpointSecurity: Option[publisher.models.API_endpointSecurity] = None) =
    retry(createOrUpdateApi(c, clientReg, endpointSecurity),
          c.retryDelay,
          c.maxRetries)(actorSystem.dispatcher, actorSystem.scheduler)

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

  def proxyAdmin(conf: AmClientParser.AmClientOptions,
                 clientReg: ClientRegistrationResponse): Future[String] = {
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

    createOrUpdateApi(conf.copy(swagger = Some(soapSwagger)),
                      clientReg,
                      Some(security))
  }

  private def writeRoles(jx: JsObject, output: Path): Path = {
    FileUtils.write(output.toFile, jx.toJson.prettyPrint)
    output
  }

  def getAndWriteRoles(c: AmClientParser.AmClientOptions): Future[String] = {
    for {
      m <- Future.sequence(c.roles.map(r =>
        am.getUserListOfRole(c.user, c.pass, r).map(users => (r, users))))
      _ <- Future.successful(
        writeRoles(m.toMap.toJson.asJsObject, c.roleJson.toPath))
    } yield s"Successfully wrote roles to ${c.roleJson.toPath}"
  }

  def setRoles(c: AmClientParser.AmClientOptions): Future[String] = {
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

    for {
      msg <- Future
        .sequence(roleUsers.map(addNew.tupled))
        .map(_ => "Imported roles")
      _ <- andLog(msg)
    } yield msg
  }
}

object AmClient extends LazyLogging {

  def apply(conf: AmClientParser.AmClientOptions): Int = {
    import Wso2Models.defaultClient

    implicit val actorSystem = ActorSystem("amclient")

    implicit val ec: ExecutionContext = actorSystem.dispatcher

    val c = conf.validate()
    val am = new ApiManagerAccessLayer(c.host, c.portOffset, c.user, c.pass)
    val amClient = new AmClient(am)

    val totalStartupTimeOut
      : FiniteDuration = (c.w2StartUpRetries + 2) * c.w2StartUpRetryDelay

    def andLog(sx: String): Future[String] = Future {
      logger.info(sx)
      sx
    }

    // This error message might not be very useful. This might
    // need to be collapsed to a terse "WSO2 Did not start successfully" or similar
    def waitForWso2ToStartup(): Future[String] = {
      for {
        _ <- andLog(
          s"Checking for status of WSO2 with ${c.w2StartUpRetries} retries and ${c.w2StartUpRetryDelay} retry delay and total timeout $totalStartupTimeOut")
        result <- am.waitForStart(c.w2StartUpRetries, c.w2StartUpRetryDelay)
        _ <- andLog("Successfully connected to WSO2")
      } yield result
    }

    def runMode(): Future[String] = {
      logger.info(s"Starting to run mode ${c.mode.name}")
      c.mode match {
        case AmClientModes.STATUS =>
          // This is already performed for every call
          Future.successful("Successfully connected to WSO2")
        case AmClientModes.CREATE_ROLES =>
          amClient.createRoles(c)
        case AmClientModes.CREATE_USER =>
          amClient.createUser(c)
        case AmClientModes.GET_ROLES_USERS =>
          amClient.getAndWriteRoles(c)
        case AmClientModes.SET_ROLES_USERS =>
          amClient.setRoles(c)
        case AmClientModes.SET_API =>
          amClient.createOrUpdateApiWithRetry(c, defaultClient)
        case AmClientModes.PROXY_ADMIN =>
          amClient.proxyAdmin(c, defaultClient)
        case x =>
          Future.failed(new Exception(s"Unsupported action '$x'"))
      }
    }

    def tx(): Try[String] =
      for {
        _ <- Try(Await.result(waitForWso2ToStartup(), totalStartupTimeOut))
        msg <- Try(Await.result(runMode(), c.defaultTimeOut))
      } yield msg

    tx() match {
      case Success(msg) =>
        actorSystem.terminate()
        println(msg)
        logger.info(msg)
        0
      case Failure(ex) =>
        actorSystem.terminate()
        val msg = s"Failed running ${c.mode} ${ex.getMessage}"
        System.err.println(msg)
        logger.error(msg)
        1
    }
  }
}

//FIXME(mpkocher)(11-15-2017) This needs to be converted to CommandLineToolRunner.
object AmClientApp extends App with timeUtils with LazyLogging {
  def run(args: Seq[String]) = {
    val startedAt = JodaDateTime.now()
    val xc =
      AmClientParser.parser.parse(args.toSeq, AmClientParser.defaults) match {
        case Some(config) => AmClient(config)
        case _ => 1
      }
    val msg =
      s"Exiting ${AmClientParser.VERSION} with exit code $xc in ${computeTimeDeltaFromNow(startedAt)} sec."
    logger.info(msg)
    sys.exit(xc)
  }
  run(args)
}
