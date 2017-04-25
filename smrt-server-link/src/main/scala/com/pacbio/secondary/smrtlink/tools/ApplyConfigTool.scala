package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.net.URL
import java.nio.file.{Files, Path}

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure}
import com.pacbio.secondary.smrtlink.models.ConfigModels.{Wso2Credentials, RootSmrtflowConfig}
import com.pacbio.common.models.XmlTemplateReader
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import scopt.OptionParser

import scala.util.Try
import spray.json._
import com.pacbio.secondary.smrtlink.models.ConfigModelsJsonProtocol


case class ApplyConfigToolOptions(rootDir: Path, templateDir: Option[Path] = None) extends LoggerConfig

object ApplyConfigConstants {

  val TOMCAT_VERSION = "apache-tomcat-8.0.26"
  val WSO2_VERSION = "wso2am-2.0.0"

  // This must be relative as
  val SL_CONFIG_JSON = "smrtlink-system-config.json"
  val WSO2_CREDENTIALS_JSON = "wso2-credentials.json"

  val JVM_ARGS = "services-jvm-args"
  val JVM_LOG_ARGS = "services-log-args"

  val STATIC_FILE_DIR = "sl"

  val UI_API_SERVER_CONFIG_JSON = "api-server.config.json"

  // Tomcat
  val TOMCAT_SERVER_XML = "server.xml"
  val TOMCAT_HTTPS_SERVER_XML = "https-server.xml"
  val TOMCAT_INDEX = "index.jsp"

  val SSL_KEYSTORE_FILE = ".keystore"
  val UI_PROXY_FILE = "_sl-ui_.xml"

  // Relative Tomcat
  val REL_TOMCAT_ENV_SH = s"$TOMCAT_VERSION/bin/setenv.sh"
  val REL_TOMCAT_SERVER_XML = s"$TOMCAT_VERSION/conf/$TOMCAT_SERVER_XML"
  val REL_TOMCAT_REDIRECT_JSP = s"$TOMCAT_VERSION/webapps/ROOT/$TOMCAT_INDEX"

  val REL_TOMCAT_UI_API_SERVER_CONFIG = s"$TOMCAT_VERSION/webapps/ROOT/$STATIC_FILE_DIR/$UI_API_SERVER_CONFIG_JSON"

  val REL_WSO2_API_DIR = s"$WSO2_VERSION/repository/deployment/server/synapse-configs/default/api"
  val REL_WSO2_LOG_DIR = s"$WSO2_VERSION/repository/logs"

  // Templates
  val T_WSO2_TEMPLATES = "templates-wso2"
  // Tomcat
  val T_TOMCAT_USERS_XML = "tomcat-users.xml"
  val T_TOMCAT_SERVER_XML = "server.xml"
  val T_INDEX_JSP = "index.jsp"
}

trait Resolver {
  val rootDir: Path
  def resolve(fileName: String) = rootDir.resolve(fileName)
}

/**
  * Resolve files relative the root Bundle Directory
  *
  * @param rootDir
  */
class BundleOutputResolver(override val rootDir: Path) extends Resolver{
  val jvmArgs = resolve(ApplyConfigConstants.JVM_ARGS)

  val jvmLogArgs = resolve(ApplyConfigConstants.JVM_LOG_ARGS)

  val smrtLinkSystemConfig = resolve(ApplyConfigConstants.SL_CONFIG_JSON)

  val tomcatEnvSh = resolve(ApplyConfigConstants.REL_TOMCAT_ENV_SH)

  val tomcatIndexJsp = resolve(ApplyConfigConstants.REL_TOMCAT_REDIRECT_JSP)

  val tomcatServerXml = resolve(ApplyConfigConstants.REL_TOMCAT_SERVER_XML)

  val uiApiServerConfig = resolve(ApplyConfigConstants.REL_TOMCAT_UI_API_SERVER_CONFIG)

  val keyStoreFile = resolve(ApplyConfigConstants.SSL_KEYSTORE_FILE)

  val wso2ApiDir = resolve(ApplyConfigConstants.REL_WSO2_API_DIR)

  val uiProxyConfig = wso2ApiDir.resolve(ApplyConfigConstants.UI_PROXY_FILE)
}

class TemplateOutputResolver(override val rootDir: Path) extends Resolver {

  val wso2TemplateDir = resolve(ApplyConfigConstants.T_WSO2_TEMPLATES)

  private def resolveWso2Template(fileName: String) =
    wso2TemplateDir.resolve(fileName)

  val indexJsp = resolve(ApplyConfigConstants.T_INDEX_JSP)
  val serverXml = resolve(ApplyConfigConstants.T_TOMCAT_SERVER_XML)
  val tomcatUsers = resolve(ApplyConfigConstants.T_TOMCAT_USERS_XML)

  val uiProxyXml = resolveWso2Template(ApplyConfigConstants.UI_PROXY_FILE)
}


object ApplyConfigUtils extends LazyLogging{

  import ConfigModelsJsonProtocol._

  private def writeAndLog(file: File, sx: String): File = {
    FileUtils.write(file, sx, "UTF-8")
    logger.debug(s"Wrote file $file")
    file
  }

  def loadSmrtLinkSystemConfig(path: Path): RootSmrtflowConfig =
    FileUtils.readFileToString(path.toFile, "UTF-8")
        .parseJson.convertTo[RootSmrtflowConfig]

  def loadWso2Credentials(path: Path): Wso2Credentials =
    FileUtils.readFileToString(path.toFile, "UTF-8")
      .parseJson.convertTo[Wso2Credentials]

  def validateConfig(c: RootSmrtflowConfig): RootSmrtflowConfig = {
    logger.warn(s"validation of config not implemented $c")
    c
  }

  def writeJvmArgs(outputFile: File, minMemory: Int, maxMemory: Int): File = {
    val sx = s"-Xmx${maxMemory}m -Xms${minMemory}m"
    writeAndLog(outputFile, sx)
  }

  def writeJvmLogArgs(outputFile: File, logDir: Path, logLevel: String): File = {
    val sx = s"--log-file $logDir/secondary-smrt-server.log --log-level $logLevel"
    writeAndLog(outputFile, sx)
  }

  def setupWso2LogDir(rootDir: Path, logDir: Path): Path = {
    val wso2LogDir = rootDir.resolve(ApplyConfigConstants.REL_WSO2_LOG_DIR)
    val symlink = logDir.resolve("wso2")
    if (! Files.exists(symlink)) {
      Files.createSymbolicLink(symlink, wso2LogDir)
    } else {
      logger.warn(s"Path $symlink already exists")
      symlink
    }
  }

  type M = Map[String, Option[String]]
  case class ApiServerConfig(auth: M, `events-url`: M, `smrt-link`: M, `smrt-link-backend`: M, `smrt-view`: M, `tech-support`: M)

  private def toApiUIServer(authUrl: URL, eventsUrl: Option[URL], smrtLink: URL, smrtLinkBackendUrl: URL, smrtView: URL, techSupport: Option[URL]): ApiServerConfig = {
    def toM(u: URL): M = Map("default-server" -> Some(u.toString))
    def toO(u: Option[URL]): M = Map("default-server" -> u.map(_.toString))
    ApiServerConfig(toM(authUrl), toO(eventsUrl), toM(smrtLink), toM(smrtLinkBackendUrl), toM(smrtView), toO(techSupport))
  }
  private def writeApiServerConfig(c: ApiServerConfig, output: File): File = {
    implicit val converterFormat = jsonFormat6(ApiServerConfig)
    val jx = c.toJson
    writeAndLog(output, jx.prettyPrint)
    output
  }

  /**
    * #6 (update_server_path_in_ui)
    * Update the UI api-server.config.json in Tomcat at /webapps/ROOT/${STATIC_DIR}/api-server.config.json
    * @param outputFile
    */
  def updateApiServerConfig(outputFile: File,
                            authUrl: URL,
                            eventsUrl: Option[URL],
                            smrtLinkUrl: URL,
                            smrtLinkBackendUrl: URL,
                            smrtViewUrl: URL, techSupportUrl: Option[URL] = None): File = {

    val c = toApiUIServer(authUrl, eventsUrl, smrtLinkUrl, smrtLinkBackendUrl, smrtViewUrl, techSupportUrl)
    writeApiServerConfig(c, outputFile)
    outputFile
  }

  def updateTomcatSetupEnvSh(output: File, tomcatMem: Int): File = {
    // double $ escapes
    val sx = "export CATALINA_OPTS=" + "\"" + s"$$CATALINA_OPTS -Xmx${tomcatMem}m -Xms${tomcatMem}m" + "\"\n"
    writeAndLog(output, sx)
  }

  /**
    * # 7 (update_tomcat)
    * Update Tomcat server.xml Template with Tomcat Port.
    * (REMOVE THIS) Update .keystore file in the root bundle dir (?) is this even used ???
    *
    * @param outputTomcatServerXml
    * @param tomcatPort
    */
  def updateTomcatConfig(outputTomcatServerXml: File,
                         tomcatServerTemplate: File,
                         tomcatPort: Int): File = {

    val xmlNode = XmlTemplateReader
        .fromFile(tomcatServerTemplate)
        .globally()
        .substitute("${TOMCAT_PORT}", tomcatPort).result()

    writeAndLog(outputTomcatServerXml, xmlNode.mkString)
  }

  /**
    * #8 (update_sl_ui)
    * - Write _sl-ui.xml to the root wso2 API dir with the tomcat host, port and static dir (e.g, "sl")
    *
    * @param outputFile
    */
  def updateUIProxyXml(outputFile: File, smrtLinkStaticUITemplateXml: File, host: String, tomcatPort: Int, smrtLinkStaticDir: String): File = {

    // Not really clear how to use this API
    def a(): String = smrtLinkStaticDir.toString
    def b(): String = tomcatPort.toString
    def c(): String = host

    val subs: Map[String, () => Any] =
      Map("${STATIC_FILE_DIR}" -> a,
        "${TOMCAT_PORT}" -> b,
        "${TOMCAT_HOST}" -> c)

    val xmlNode = XmlTemplateReader.
        fromFile(smrtLinkStaticUITemplateXml)
        .globally()
        .substituteMap(subs).result()

    writeAndLog(outputFile, xmlNode.mkString)
  }

  /**
    * #9 (update_redirect)
    * Write the index.jsp with the wso2 HOST, port and the static dir (e.g., "sl")
    *
    * @return
    */
  def updateWso2Redirect(outputFile: File, indexJspTemplate: File, host: String, wso2Port: Int, staticDirName: String): File = {

    // This is not XML, use a template search/replace model. Note, Host doesn't NOT have the protocol
    val sx = FileUtils.readFileToString(indexJspTemplate)
    val output = sx
        .replace("${STATIC_FILE_DIR}", staticDirName)
        .replace("${WSO2_HOST}", host)
        .replace("${WSO2_PORT}", wso2Port.toString)

    writeAndLog(outputFile, output)
  }

  /**
   * 1.  Load and Validate smrtlink-system-config.json
   * 2.  Setup Log to location defined in config JSON (Not Applicable. JVM_OPTS will do this?)
   * 3.  Null host (?) clarify this interface (Unclear what to do here. Set the host to fqdn?)
   * 4.  (write_services_jvm_args) write jvm args (/ROOT_BUNDLE/services-jvm-args)
   * 5.  (write_services_args) write jvm log (/ROOT_BUNDLE/services-log-args)
   * 6.  (update_server_path_in_ui) Update UI api-server.config.json within the Tomcat dir (webapps/ROOT/sl/api-server.config.json)
   * 7.  (update_tomcat) Update Tomcat XML (TOMCAT_ROOT/conf/server.xml) and .keystore file in /ROOT_BUNDLE
   * 8.  (update_sl_ui) Updates wso2-2.0.0/repository/deployment/server/synapse-configs/default/api/_sl-ui_.xml
   * 9.  (update_redirect) write index.jsp to tomcat root
   * 10. (update_wso2_conf) Updates wso2-2.0.0/repository/conf/user-mgt.xml and wso2-2.0.0/repository/conf/jndi.properties
   */
  def run(opts: ApplyConfigToolOptions): String = {

    // if templateDir is not provided, a dir "templates" dir within
    // the rootBundleDir
    val rootBundleDir = opts.rootDir

    val templatePath = opts.templateDir.getOrElse(opts.rootDir.resolve("templates"))

    val smrtLinkConfigPath = rootBundleDir.resolve(ApplyConfigConstants.SL_CONFIG_JSON)

    val smrtLinkConfig = loadSmrtLinkSystemConfig(smrtLinkConfigPath)

    val c = validateConfig(smrtLinkConfig)

    val wso2CredentialsPath = rootBundleDir.resolve(ApplyConfigConstants.WSO2_CREDENTIALS_JSON)

    val wso2Credentials = loadWso2Credentials(wso2CredentialsPath)

    // If the DNS name is None, resolve the FQDN of the host and log a warning. This interface
    // needs to be well-defined.
    val host = c.smrtflow.server.dnsName match {
      case Some("0.0.0.0") =>
        java.net.InetAddress.getLocalHost.getCanonicalHostName
      case Some(x) => x
      case _ =>
        val fqdnHost = java.net.InetAddress.getLocalHost.getCanonicalHostName
        logger.warn(s"Null dnsName set for smrtflow.server.dnsName. Using $fqdnHost")
        fqdnHost
    }

    // Config parameters that are hardcoded
    val defaultLogLevel = "INFO"
    val wso2Port = 8243

    // What's the difference between these?
    val authUrl = new URL(s"https://$host:$wso2Port")
    val smrtLinkUrl = new URL(s"https://$host:$wso2Port")
    // raw SLA port
    val smrtLinkBackEndUrl = new URL(s"http://$host:${c.smrtflow.server.port}")
    val smrtViewUrl = new URL(s"http://$host:${c.pacBioSystem.smrtViewPort}")
    val techSupportUrl: Option[URL] = None

    val resolver = new BundleOutputResolver(rootBundleDir)

    val templateResolver = new TemplateOutputResolver(templatePath)

    // #4
    writeJvmArgs(resolver.jvmArgs.toFile, c.pacBioSystem.smrtLinkServerMemoryMin, c.pacBioSystem.smrtLinkServerMemoryMax)

    // #5
    writeJvmLogArgs(resolver.jvmLogArgs.toFile, c.pacBioSystem.logDir, defaultLogLevel)

    // #6
    updateApiServerConfig(resolver.uiApiServerConfig.toFile, authUrl, c.smrtflow.server.eventUrl, smrtLinkUrl, smrtLinkBackEndUrl, smrtViewUrl, techSupportUrl)

    // #7a
    updateTomcatSetupEnvSh(resolver.tomcatEnvSh.toFile, c.pacBioSystem.tomcatMemory)

    // 7b
    updateTomcatConfig(resolver.tomcatServerXml.toFile, templateResolver.serverXml.toFile, c.pacBioSystem.tomcatPort)

    // #8
    updateUIProxyXml(resolver.uiProxyConfig.toFile, templateResolver.uiProxyXml.toFile, host, c.pacBioSystem.tomcatPort, ApplyConfigConstants.STATIC_FILE_DIR)

    // #9
    updateWso2Redirect(resolver.tomcatIndexJsp.toFile, templateResolver.indexJsp.toFile, host, wso2Port, ApplyConfigConstants.STATIC_FILE_DIR)

    // #10
    new SetPasswordTool(SetPasswordArgs(opts.rootDir, wso2Credentials.wso2User, wso2Credentials.wso2Password)).updateWso2ConfFiles()

    setupWso2LogDir(rootBundleDir, c.pacBioSystem.logDir)

    "Successfully Completed apply-config"
  }
}


object ApplyConfigTool extends CommandLineToolRunner[ApplyConfigToolOptions] {

  val VERSION = "0.1.0"
  val DESCRIPTION = "Apply smrtlink-system-config.json to SubComponents of the SMRT Link system"
  val toolId = "smrtflow.tools.apply_config"

  val defaults = ApplyConfigToolOptions(null)

  /**
    * This should do more validation on the required template files
    *
    * @param p Root Path to th bundle directory
    * @return
    */
  def validateIsDir(p: File): Either[String, Unit] = {
    if (Files.isDirectory(p.toPath)) Right(Unit)
    else Left(s"$p must be a directory")
  }

  lazy val parser = new OptionParser[ApplyConfigToolOptions]("apply-config") {

    arg[File]("root-dir")
        .action((x, c) => c.copy(rootDir = x.toPath.toAbsolutePath))
        .validate(validateIsDir)
        .text("Root directory of the SMRT Link Analysis GUI bundle")

    opt[File]("template-dir")
        .action((x, c) => c.copy(templateDir = Some(x.toPath)))
        .validate(validateIsDir)
        .text("Override path to template directory. By default 'template' dir within <ROOT-DIR> will be used")

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    override def showUsageOnError = false

    // add the shared `--debug` and logging options
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  override def runTool(opts: ApplyConfigToolOptions): Try[String] =
    Try { ApplyConfigUtils.run(opts) }

  // for backward compat with old API
  def run(config: ApplyConfigToolOptions) = Left(ToolFailure(toolId, 0, "NOT SUPPORTED"))
}

object ApplyConfigToolApp extends App {
  import ApplyConfigTool._
  runnerWithArgs(args)
}
