package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.{Path, Files}

import com.pacbio.secondary.analysis.tools.CommandLineToolVersion
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import scopt.OptionParser

object SetPasswordConstants {
  val WSO2_CREDENTIALS_JSON = "wso2-credentials.json"
}

case class SetPasswordArgs(rootDir: Path = null, user: String = null, pass: String = null)

object SetPasswordToolParser extends CommandLineToolVersion {
  val VERSION = "0.1.0"
  val TOOL_ID = "pbscala.tools.bundler-set-password"

  val defaults = SetPasswordArgs()

  val parser = new OptionParser[SetPasswordArgs]("bundler-set-password") {
    head("Set WSO2 Admin Credentials", VERSION)

    arg[File]("root-dir")
      .action((x, c) => c.copy(rootDir = x.toPath.toAbsolutePath))
      .validate(p => if (Files.isDirectory(p.toPath)) success else failure(s"$p must be a directory"))
      .text("Root directory of the SMRT Link Analysis GUI bundle")

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
        showToolVersion(TOOL_ID, VERSION)
        sys.exit(0)
      }
      .text("Show tool version and exit")
  }
}

class SetPasswordTool(args: SetPasswordArgs) extends LazyLogging {
  import SetPasswordConstants._

  def run(): Int = {
    val jsonString =
      s"""
        |{
        |  "wso2User": "${args.user}",
        |  "wso2Password": "${args.pass}"
        |}
      """.stripMargin

    val wso2CredentialsPath = args.rootDir.resolve(WSO2_CREDENTIALS_JSON)

    FileUtils.write(wso2CredentialsPath.toFile, jsonString, "UTF-8")
    0
  }
}

object SetPasswordToolApp extends App {
  def run(args: Seq[String]) = {
    val xc = SetPasswordToolParser.parser.parse(args.toSeq, SetPasswordToolParser.defaults) match {
      case Some(a) => new SetPasswordTool(a).run()
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
