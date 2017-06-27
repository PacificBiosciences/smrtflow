package com.pacbio.secondary.smrtlink.tools

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.apache.commons.io.FileUtils
import scopt.OptionParser
import spray.json._

import scala.util.{Failure, Success, Try}
import com.pacbio.common.file.FileSizeFormatterUtil
import com.pacbio.common.models.{Constants, PacBioComponentManifest}
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.analysis.techsupport.{TechSupportConstants, TechSupportUtils}
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure, ToolSuccess}
import com.pacbio.secondary.smrtlink.models.ConfigModels.RootSmrtflowConfig
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols._


case class TechSupportFileBundlerOptions(rootUserData: Path, output: Path, user: String,
                                         dnsName: Option[String],
                                         smrtLinkVersion: Option[String],
                                         smrtLinkSystemId: Option[UUID],
                                         comment: String
                                        ) extends LoggerConfig


object TechSupportFileBundler extends CommandLineToolRunner[TechSupportFileBundlerOptions] with ConfigLoader with FileSizeFormatterUtil {

  override val VERSION = "0.3.0"
  override val DESCRIPTION =
    s"""
      |Tech Support Bundler $VERSION
      |
      |Create TechSupport bundle for failed SMRT Link Install
      |
      |Default values for Smrt Link Version, System Id and DNS will be extracted from
      |the smrtlink-system-config.json in the userdata/config.
      |
      |If all of these values are overridden, the smrtlink-system-config.json will not be used (or required).
    """.stripMargin
  override val toolId: String = "smrtflow.tools.tech_support_bundler"

  def getDefault(sx: String): Option[String] = Try { conf.getString(sx)}.toOption

  val defaults = TechSupportFileBundlerOptions(
    Paths.get("userdata"),
    Paths.get(TechSupportConstants.DEFAULT_TS_BUNDLE_TGZ),
    System.getProperty("user.name"),
    getDefault("smrtflow.server.dnsName"),
    None,
    None,
    s"Created by $toolId smrtflow version ${Constants.SMRTFLOW_VERSION}"
  )

  val parser = new OptionParser[TechSupportFileBundlerOptions]("techsupport-bundler") {

    head(DESCRIPTION)

    arg[File]("userdata")
        .action { (x, c) => c.copy(rootUserData = x.toPath) }
        .validate(validateRootDir)
        .text(s"Path to Root SMRT Link System userdata dir (e.g, /my-system/root/userdata.")

    opt[String]("output")
        .action { (x, c) => c.copy(output = Paths.get(x).toAbsolutePath) }
        .validate(validateDoesNotExist)
        .text(s"Output TechSupport bundle output (tar.gz) file. Default '${defaults.output.toAbsolutePath}'")

    opt[String]("user")
        .action { (x, c) => c.copy(user = x) }
        .validate(validateDoesNotExist)
        .text(s"Optional user to create TechSupport bundle output (tgz) file. Default ${defaults.user}")

    opt[String]("comment")
        .action { (x, c) => c.copy(comment = x) }
        .text(s"Optional user comment or case number for TechSupport bundle output (tgz) file. Default ${defaults.comment}")

    def overrideMessage(sx: String) = s"Override for $sx. (pulled from userdata/smrtlink-system-config.json)"
    // I'm not sure these make sense, but I've added this for testing and for access by DEP. Ideally, all of these
    // These values should be configured globally from the smrtlink-system-config.json file. Adding these overrides
    // adds flexibility at the cost of having a single location where this data
    // is pulled from. This can result in inconsistent state between the three values.
    opt[String]("dns")
        .action { (x, c) => c.copy(dnsName = Some(x)) }
        .text(overrideMessage("SMRT Link System DNS name"))

    opt[String]("smrtlink-id")
        .validate(validateUUID)
        .action{ (x, c) => c.copy(smrtLinkSystemId = Some(UUID.fromString(x)))}
        .text(overrideMessage("SMRT Link System Id"))

    opt[String]("smrtlink-version")
        .action{ (x, c) => c.copy(smrtLinkVersion = Some(x))}
        .text(overrideMessage("SMRT Link System Version"))

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

    override def errorOnUnknownArgument = false

    override def showUsageOnError = false

    def validateUUID(sx: String): Either[String, Unit] = {
      Try (UUID.fromString(sx))
          .map(_ => success)
          .getOrElse(Left(s"Invalid uuid format for $sx"))
    }

  }

  def hasRequired(fx:(Path => Boolean))(path: Path) = {
    if (Files.exists(path: Path) && fx(path)) Success(path)
    else Failure(throw new IOException(s"Unable to find required resource $path"))
  }

  val hasRequiredDir = hasRequired(Files.isDirectory(_))(_)
  val hasRequiredFile = hasRequired(Files.exists(_))(_)

  /**
    * Validate the the log subdir under userdata exist.
    * This is the minimal data that is required.
    *
    *
    * @param rootPath SL Userdata root path
    * @return
    */
  def hasRequiredSubdirs(rootPath: Path): Try[Path] = {
    // Only make sure the log dir exists. The smrtlink-system-config.json
    // will be dynamically load from the userdata/conf dir.
    hasRequiredDir(rootPath.resolve(TechSupportUtils.TS_REQ_INSTALL(1)))
  }

  // Wrap for validation at the Scopt level to fail early
  def validateRootDir(file: File): Either[String, Unit] = {
    hasRequiredSubdirs(file.toPath) match {
      case Success(_) => Right(Unit)
      case Failure(ex) => Left(s"${ex.getMessage}")
    }
  }

  def validateDoesNotExist(sx: String): Either[String, Unit] = {
    val px = Paths.get(sx).toAbsolutePath
    if (Files.exists(px)) Left(s"File already exists. Please move or rename file $px")
    else Right(Unit)
  }

  // This is pretty painful to load the SMRT Link System version and System Id

  private def loadManifest(file: File): Seq[PacBioComponentManifest] = {
    val sx = FileUtils.readFileToString(file, "UTF-8")
    sx.parseJson.convertTo[Seq[PacBioComponentManifest]]
  }

  private def loadSystemConfig(file: File): RootSmrtflowConfig = {
    val sx = FileUtils.readFileToString(file, "UTF-8")
    val c = sx.parseJson.convertTo[RootSmrtflowConfig]
    logger.info(s"Successfully Loaded config from $file")
    c
  }


  private def getSmrtLinkVersionFromConfig(smrtLinkSystemConfig: RootSmrtflowConfig): Option[String] = {
    smrtLinkSystemConfig.smrtflow.server.manifestFile
        .map(p => loadManifest(p.toFile))
        .flatMap(manifests => manifests.find(m => m.id == "smrtlink"))
        .map(n => n.version)
  }

  def getRequired[T](field: String, opt: Option[T]): Try[T] = {
    opt.map(v => Success(v)).getOrElse(Failure(throw new Exception(s"Unable to get required value, $field, from config")))
  }

  def getOr[T](rawOpt: Option[T], p: Path, f:(Path => Option[T])): Option[T] =
    rawOpt.map(x => Some(x)).getOrElse(f(p))

  def getSmrtLinkVersionFromConfigFile(p: Path): Option[String] =
    getSmrtLinkVersionFromConfig(loadSystemConfig(p.toFile))

  def getSmrtLinkIdFromConfigFile(p: Path): Option[UUID] =
    loadSystemConfig(p.toFile).pacBioSystem.smrtLinkSystemId

  def getDnsNameFromConfigFile(p: Path): Option[String] =
    loadSystemConfig(p.toFile).smrtflow.server.dnsName

  override def runTool(c: TechSupportFileBundlerOptions): Try[String] = {

    val px = c.rootUserData.toAbsolutePath()

    // This will only be used if necessary
    val configPath = px.resolve(s"config/smrtlink-system-config.json")

    for {
      systemId <- getRequired[UUID]("SMRT Link System Id", getOr[UUID](c.smrtLinkSystemId, configPath, getSmrtLinkIdFromConfigFile))
      systemVersion <- getRequired[String]("SMRT Link System Version", getOr[String](c.smrtLinkVersion, configPath, getSmrtLinkVersionFromConfigFile))
      dnsName <- getRequired[String]("SMRT Link DNS Name", getOr[String](c.dnsName, configPath, getDnsNameFromConfigFile))
      tgzPath <-  Try { TechSupportUtils.writeSmrtLinkSystemStatusTgz(systemId, px, c.output, c.user, Some(systemVersion), Some(dnsName), Some(c.comment))}
    } yield s"Successfully wrote TechSupport Bundle to $tgzPath (${humanReadableByteSize(tgzPath.toFile.length())})"
  }

  // To adhere to the fundamental interface. Other tools need to migrate to use
  // new runnerWithTryAndExit model
  def run(c: TechSupportFileBundlerOptions): Either[ToolFailure, ToolSuccess] =
    Left(ToolFailure(toolId, 0, "NOT Supported"))

}


object TechSupportFileBundlerApp extends App {
  import TechSupportFileBundler._

  runnerWithArgsAndExit(args)
}
