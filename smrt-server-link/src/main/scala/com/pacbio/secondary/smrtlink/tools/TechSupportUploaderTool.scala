package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.net.URL
import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import scopt.OptionParser

import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.analysis.techsupport.TechSupportConstants
import com.pacbio.secondary.smrtlink.analysis.tools.{
  CommandLineToolRunner,
  ToolFailure,
  ToolSuccess
}
import com.pacbio.secondary.smrtlink.client.EventServerClient
import com.pacbio.secondary.smrtlink.file.FileSizeFormatterUtil

case class TechSupportUploaderOptions(url: URL,
                                      apiSecret: String,
                                      path: Path,
                                      timeOut: Duration = 300.seconds)
    extends LoggerConfig

/**
  * Created by mkocher on 4/25/17.
  *
  * Commandline Tool to upload to PacBio Eve server
  *
  *
  */
object TechSupportUploaderTool
    extends CommandLineToolRunner[TechSupportUploaderOptions]
    with ConfigLoader
    with FileSizeFormatterUtil {

  override val VERSION = "0.1.0"
  override val toolId: String = "tech_support_uploader"
  override val DESCRIPTION =
    """
      |Tool to Upload a TechSupport TGZ file to PacBio. The TGZ file must contain a
      |tech-support-manifest.json file in the root directory.
    """.stripMargin

  lazy val apiSecret = conf.getString("smrtflow.event.apiSecret")

  lazy val eveUrl = Try {
    new URL(conf.getString("smrtflow.server.eventUrl"))
  }.toOption
    .getOrElse(new URL("http://localhost:8083"))

  val defaults = TechSupportUploaderOptions(
    eveUrl,
    apiSecret,
    Paths.get(TechSupportConstants.DEFAULT_TS_BUNDLE_TGZ))

  val parser =
    new OptionParser[TechSupportUploaderOptions]("tech-support-uploader") {
      head(DESCRIPTION)

      arg[File]("tgz")
        .action({ (x, c) =>
          c.copy(path = x.toPath)
        })
        .text("Path to TechSupport TGZ file")

      opt[String]("url")
        .action({ (x, c) =>
          c.copy(url = new URL(x))
        })
        .text(s"Remote PacBio Server URL (Default: ${defaults.url})")

      opt[Int]("timeout")
        .action({ (x, c) =>
          c.copy(timeOut = Duration(x, SECONDS))
        })
        .text(s"Timeout for upload request ${defaults.timeOut}")

      opt[Unit]('h', "help")
        .action { (x, c) =>
          showUsage
          sys.exit(0)
        }
        .text("Show Options and exit")

      opt[Unit]("version")
        .action { (x, c) =>
          showVersion
          sys.exit(0)
        }
        .text("Show tool version and exit")

      LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

      override def errorOnUnknownArgument = false

      override def showUsageOnError = false

    }

  override def runTool(c: TechSupportUploaderOptions): Try[String] = {

    implicit val timeOut = c.timeOut

    implicit val actorSystem = ActorSystem("ts-uploader")
    val client = new EventServerClient(c.url, c.apiSecret)

    val fileSize = humanReadableByteSize(c.path.toFile.length())

    logger.debug(s"Getting status from ${client.statusUrl}")
    logger.debug(s"Attempting to upload $fileSize to ${client.toUploadUrl}")

    val fx = for {
      _ <- client.getStatus.map { status =>
        logger.info(
          s"Got Server ${status.id} ${status.uuid} ${status.message}"); status
      }
      event <- client.upload(c.path)
    } yield s"Create System Event ${event.uuid}"

    fx.onComplete { _ =>
      actorSystem.terminate()
    }

    runAndBlock(fx, timeOut)
  }

  // To adhere to the fundamental interface. Other tools need to migrate to use
  // new runnerWithTryAndExit model
  def run(c: TechSupportUploaderOptions): Either[ToolFailure, ToolSuccess] =
    Left(ToolFailure(toolId, 0, "NOT Supported"))

}

object TechSupportUploaderApp extends App {
  import TechSupportUploaderTool._
  runnerWithArgsAndExit(args)
}
