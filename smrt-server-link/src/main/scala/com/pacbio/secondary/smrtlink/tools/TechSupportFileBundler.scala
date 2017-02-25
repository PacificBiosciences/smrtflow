package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.{Files, Path, Paths}

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure, ToolSuccess}
import com.pacbio.secondary.smrtlink.techsupport.TechSupportFailedInstallBuilder
import scopt.OptionParser

import scala.util.{Failure, Success, Try}

case class TechSupportFileBundlerOptions(rootUserData: Path, output: Path) extends LoggerConfig


object TechSupportFileBundler extends CommandLineToolRunner[TechSupportFileBundlerOptions] {

  override val VERSION = "0.1.0"
  override val toolId: String = "smrtflow.tools.tech_support_bundler"

  val defaults = TechSupportFileBundlerOptions(
    Paths.get("userdata"),
    Paths.get("tech-support-bundle.tgz")
  )

  val parser = new OptionParser[TechSupportFileBundlerOptions]("techsupport-bundler") {

    head("Create TechSupport bundle for failed SMRT Link Installs")

    arg[File]("userdata")
        .action {(x, c) => c.copy(rootUserData = x.toPath)}
        .validate(validateRootDir)
        .text(s"Path to Root SMRT Link System userdata dir (e.g, /my-system/root. " +
            s"The SMRT Link System root dir should have " +
            s"{userdata|current} sub directories.")

    opt[String]("output")
        .action { (x, c) => c.copy(output = Paths.get(x).toAbsolutePath)}
        .validate(validateDoesNotExist)
        .text(s"Output TechSupport bundle output (tgz) file. Default '${defaults.output.toAbsolutePath}'")

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

  }

  // Wrap for validation at the Scopt level to fail early
  def validateRootDir(file: File): Either[String, Unit] = {
    TechSupportFailedInstallBuilder.hasRequiredSubdirs(file.toPath) match {
      case Success(_) => Right(Unit)
      case Failure(ex) => Left(s"${ex.getMessage}")
    }
  }

  def validateDoesNotExist(sx: String): Either[String, Unit] = {
    val px = Paths.get(sx).toAbsolutePath
    if (Files.exists(px)) Left(s"File already exists. Please move or rename file $px")
    else Right(Unit)
  }

  override def runTool(c: TechSupportFileBundlerOptions): Try[String] =
      Try { TechSupportFailedInstallBuilder(c.rootUserData, c.output) }
          .map(output => s"Successfully wrote TechSupport Bundle to $output (${output.toFile.length() / 1024} Kb)")

  // To adhere to the fundamental interface. Other tools need to migrate to use
  // new runnerWithTryAndExit model
  def run(c: TechSupportFileBundlerOptions): Either[ToolFailure, ToolSuccess] =
    Left(ToolFailure(toolId, 0, "NOT Supported"))

}


object TechSupportFileBundlerApp extends App {
  import TechSupportFileBundler._

  runnerWithArgsAndExit(args)
}
