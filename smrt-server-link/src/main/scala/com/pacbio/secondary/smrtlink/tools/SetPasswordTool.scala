package com.pacbio.secondary.smrtlink.tools

import java.io.File

import scala.util.Try

import org.apache.commons.io.FileUtils
import scopt.OptionParser
import spray.json._
import DefaultJsonProtocol._

import com.pacbio.logging.LoggerConfig
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure}

case class SetPasswordArgs(credsJson: File = null, user: String = null, pass: String = null) extends LoggerConfig

object SetPasswordToolParser extends CommandLineToolRunner[SetPasswordArgs] {
  override val VERSION = "0.1.0"
  override val toolId = "pbscala.tools.bundler-set-password"
  override val DESCRIPTION =
    """
      |Update the WSO2 user and password Credentials JSON file
    """.stripMargin

  val defaults = SetPasswordArgs()

  val parser = new OptionParser[SetPasswordArgs]("bundler-set-password") {
    head("Set WSO2 Admin Credentials", VERSION)

    arg[File]("creds-json")
      .action((x, c) => c.copy(credsJson = x))
      .text("Location of the WSO2 credentials JSON file")

    opt[String]('u', "user")
      .action((x, c) => c.copy(user = x))
      .text("WSO2 Admin Username")
      .required()

    opt[String]('p', "password")
      .action((x, c) => c.copy(pass = x))
      .text("WSO2 Admin Password")
      .required()

    opt[Unit]('h', "help")
      .action { (x, c) =>
        showUsage()
        sys.exit(0)
      }
      .text("Show options and exit")

    opt[Unit]("version")
      .action { (x, c) =>
          showVersion
          sys.exit(0)
      }
      .text("Show tool version and exit")
  }

  def writeCreds(user: String, password: String, credsFile: File): File = {
    val jx: Map[String, JsValue] = Map(
      "wso2User" -> JsString(user),
      "wso2Password" -> JsString(password))

    val sx = JsObject(jx).toJson.prettyPrint
    FileUtils.write(credsFile, sx, "UTF-8")
    credsFile
  }

  override def runTool(opts: SetPasswordArgs): Try[String] = {
    Try { writeCreds(opts.user, opts.pass, opts.credsJson)}
        .map(f => s"Successfully sWrote credentials to $f")
  }

  // Legacy interface
  def run(c: SetPasswordArgs) = Left(ToolFailure(toolId, 1, "NOT SUPPORTED"))

}


object SetPasswordToolApp extends App {
  import SetPasswordToolParser._
  runnerWithArgsAndExit(args)
}
