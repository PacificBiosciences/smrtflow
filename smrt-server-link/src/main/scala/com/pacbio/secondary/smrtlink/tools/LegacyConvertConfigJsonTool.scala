package com.pacbio.secondary.smrtlink.tools

import java.io.{BufferedWriter, File, FileWriter}
import java.net.URL
import java.nio.file.Paths

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure, ToolSuccess}
import com.pacbio.secondary.smrtlink.models.ConfigModels._
import com.pacbio.secondary.smrtlink.models.ConfigModelsJsonProtocol
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser
import spray.json._

import scala.util.{Failure, Success, Try}


/**
  * Legacy Tool to convert the Legacy config.json schema to the V2 to SL System Config format.
  *
  * Created by mkocher on 1/19/17.
  */
case class LegacyConvertOptions(file: File, output: File) extends LoggerConfig

object LegacyConvertConfigJsonTool extends CommandLineToolRunner[LegacyConvertOptions] {

  import ConfigModelsJsonProtocol._

  val toolId: String = "smtflow.tools.legacy_convert"
  val VERSION = "0.1.1"
  val DESCRIPTION =
    """
      |"Convert Legacy config.json to SMRT Link System Config JSON (smrt-system-config.json)"
    """.stripMargin

  val defaults = LegacyConvertOptions(null, Paths.get("smrtlink-system-config").toFile)

  val parser = new OptionParser[LegacyConvertOptions]("legacy-config-converter") {
    head("Convert Legacy config.json to V2 format")
    note(DESCRIPTION)
    arg[File]("config").action((x, c) => c.copy(file = x)).text("Path to config.json schema V1 format")
    opt[File]("output").action((x, c) => c.copy(output = x)).text(s"Output path of Schema V2 format (default ${defaults.output})")

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"


    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

  }

  // This should be pushed back to a central location
  object LoadDefaultConfig extends ConfigLoader {

    def getSmrtflowPacBioSystemConfig = {
      val tmpDir = Paths.get("/tmp")
      val logDir = Paths.get(".")
      val pgDataDir = Paths.get("./pgDataDir")
      SmrtflowPacBioSystemConfig(tmpDir, logDir, pgDataDir)
    }

    def getSmrtflowDbPropertiesConfig = {
      val port = conf.getInt("smrtflow.db.properties.portNumber")
      val name = conf.getString("smrtflow.db.properties.databaseName")
      val user = conf.getString("smrtflow.db.properties.user")
      val server = conf.getString("smrtflow.db.properties.serverName")
      val password = conf.getString("smrtflow.db.properties.password")
      SmrtflowDbPropertiesConfig(name, user, password, port, server)
    }

    def getSmrtflowDbConfig =
      SmrtflowDbConfig(properties = getSmrtflowDbPropertiesConfig)

    def getSmrtflowEngineConfig = {
      val jobRoot = Paths.get(conf.getString("smrtflow.engine.jobRootDir"))
      val maxWorkers = conf.getInt("smrtflow.engine.maxWorkers")
      val presetXml = Try {Paths.get(conf.getString("smrtflow.engine.pbsmrtpipePresetXml"))}.toOption
      SmrtflowEngineConfig(
        jobRootDir = jobRoot,
        maxWorkers=maxWorkers,
        pbsmrtpipePresetXml=presetXml)
    }


    def getSmrtflowServerConfig = {

      val port = conf.getInt("smrtflow.server.port")
      val manifest = Try { Paths.get(conf.getString("smrtflow.server.manifestFile"))}.toOption
      val eventUrl = Try { new URL(conf.getString("smrtflow.server.eventUrl"))}.toOption
      val dnsName = Try { conf.getString("smrtflow.server.dnsName")}.toOption

      SmrtflowServerConfig(port, manifest, eventUrl, dnsName)
    }

    def getSmrtflowConfig =
      SmrtflowConfig(getSmrtflowServerConfig,
        getSmrtflowEngineConfig,
        getSmrtflowDbConfig)

    def default: RootSmrtflowConfig =
      RootSmrtflowConfig(getSmrtflowConfig,
        getSmrtflowPacBioSystemConfig,
        Some(s"Default Config"))
  }

  case class LegacyConfig(
                             PB_MANIFEST: Option[String],
                             HOST: Option[String],
                             PB_SERVICES_NWORKERS: Int,
                             PB_SERVICES_ANALYSIS_PORT: Int,
                             PB_SERVICES_PORT: Int,
                             PB_SMRT_VIEW_PORT: Int,
                             PB_SERVICES_ANALYSIS_MEM_MAX: Int,
                             PB_SERVICES_ANALYSIS_MEM_MIN: Int,
                             PB_SERVICES_MEM: Int,
                             PB_JOB_ROOT: String,
                             PB_SMRTPIPE_PRESET_XML: Option[String],
                             PB_TMP_DIR: String,
                             PB_LOG_DIR: String,
                             PB_SERVICES_EVENT_URL: Option[String]
                         )

  def loadLegacyConfig(file: File): LegacyConfig = {
    implicit val legacyFormat = jsonFormat14(LegacyConfig)
    scala.io.Source.fromFile(file)
        .mkString
        .parseJson
        .convertTo[LegacyConfig]
  }

  def writeConfig(c: RootSmrtflowConfig, output: File): File = {
    val bw = new BufferedWriter(new FileWriter(output))
    bw.write(c.toJson.prettyPrint.toString)
    bw.close()
    output
  }

  def mergeConfig(c: LegacyConfig, r: RootSmrtflowConfig): RootSmrtflowConfig  = {

    val engine = r.smrtflow.engine.copy(
      maxWorkers = c.PB_SERVICES_NWORKERS,
      jobRootDir = Paths.get(c.PB_JOB_ROOT),
      pbsmrtpipePresetXml = c.PB_SMRTPIPE_PRESET_XML.map(Paths.get(_))
    )

    val server = r.smrtflow.server.copy(
      port = c.PB_SERVICES_ANALYSIS_PORT,
      manifestFile = c.PB_MANIFEST.map(Paths.get(_).toAbsolutePath),
      eventUrl = c.PB_SERVICES_EVENT_URL.map(x => new URL(x)),
      dnsName = c.HOST
    )

    val system = r.pacBioSystem.copy(
      tmpDir = Paths.get(c.PB_TMP_DIR),
      logDir = Paths.get(c.PB_LOG_DIR),
      tomcatPort = c.PB_SERVICES_PORT,
      smrtViewPort = c.PB_SMRT_VIEW_PORT,
      tomcatMemory = c.PB_SERVICES_MEM,
      smrtLinkServerMemoryMax = c.PB_SERVICES_ANALYSIS_MEM_MAX,
      smrtLinkServerMemoryMin = c.PB_SERVICES_ANALYSIS_MEM_MIN
    )

    val smrtflow = r.smrtflow.copy(server = server, engine = engine)
    r.copy(pacBioSystem = system, smrtflow = smrtflow)
  }

  def convertLegacy(c: LegacyConfig, r: RootSmrtflowConfig) = mergeConfig(c, r)

  def run(c: LegacyConvertOptions): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()

    val defaultSystemConfig = LoadDefaultConfig.default

    val tx = for {
      v1 <- Try {loadLegacyConfig(c.file)}
      v2 <- Try { convertLegacy(v1, defaultSystemConfig)}
      message <- Try { s"Created by $toolId $VERSION at $startedAt"}
      output <- Try { writeConfig(v2.copy(comment = Some(message)), c.output)}
    } yield output


    tx match {
      case Success(_) => Right(ToolSuccess(toolId, computeTimeDelta(JodaDateTime.now(), startedAt)))
      case Failure(ex) => Left(ToolFailure(toolId, computeTimeDelta(JodaDateTime.now(), startedAt), s"Failed to convert ${ex.getMessage}"))
    }
  }
}

object LegacyConvertConfigJsonToolApp extends App {
  import LegacyConvertConfigJsonTool._
  runner(args)
}
