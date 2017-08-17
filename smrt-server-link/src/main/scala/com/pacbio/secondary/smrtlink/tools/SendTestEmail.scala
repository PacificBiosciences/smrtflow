
package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.Paths

import java.util.UUID
import java.net.URL

import scala.util.{Try, Success,Failure}
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.language.postfixOps

import scopt.OptionParser
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.models.ConfigModels.RootSmrtflowConfig
import com.pacbio.secondary.smrtlink.analysis.tools.{CommandLineToolRunner, ToolFailure, ToolSuccess}
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.mail.PbMailer


case class SendTestEmailOptions(email: String,
                                host: Option[String],
                                port: Int,
                                user: Option[String],
                                password: Option[String]) extends LoggerConfig

object SendTestEmail extends PbMailer {
  def apply(c: SendTestEmailOptions): Try[String] = {
    val startedAt = JodaDateTime.now()
    val job = JobModels.EngineJob(1, UUID.randomUUID(), "fake test job", "Hello world!", startedAt, startedAt, AnalysisJobStates.SUCCESSFUL, "pbsmrtpipe", "/", "", Some("nobody"), Some(c.email), None)
    val jobsBaseUrl = new URL("http://localhost:8243/sl/#/analysis/job")
    Try {
      Await.result(sendEmail(job, jobsBaseUrl, c.host, c.port, c.user, c.password), 30.seconds)
    }
  }
}

object SendTestEmailTool
    extends CommandLineToolRunner[SendTestEmailOptions]
    with SmrtLinkConfigProvider
    with EngineCoreConfigLoader
    with PbsmrtpipeConfigLoader {

  import ConfigModelsJsonProtocol._

  override val VERSION = "0.1.0"
  override val toolId: String = "tech_support_uploader"
  override val DESCRIPTION =
    """
      |Tool to test sending notification emails upon job completion.
    """.stripMargin

  val defaults = SendTestEmailOptions(null, mailHost(), mailPort(), mailUser(), mailPassword())

  def loadConfig(file: File): RootSmrtflowConfig = {
    scala.io.Source.fromFile(file)
        .mkString
        .parseJson
        .convertTo[RootSmrtflowConfig]
  }

  val parser = new OptionParser[SendTestEmailOptions]("send-test-email") {
    head(DESCRIPTION)
    arg[String]("email")
      .action({(e, c) => c.copy(email = e)})
      .text("Email address to send test message to (required)")
    opt[String]("host")
      .action({(h, c) => c.copy(host = Some(h))})
      .text(s"Mail server host name ${defaults.host.getOrElse("undefined")}")
    opt[Int]("port")
      .action({(p, c) => c.copy(port=p)})
      .text(s"SMTP port number ${defaults.port}")
    opt[String]("user")
      .action({(u, c) => c.copy(user = Some(u))})
      .text(s"SMTP login user ${defaults.user.getOrElse("undefined")}")
    opt[String]("password")
      .action({(x, c) => c.copy(password = Some(x))})
      .text(s"SMTP login password ${defaults.password.getOrElse("undefined")}")
    opt[String]("config-json")
      .action({(j, c) =>
        val cfg = loadConfig(Paths.get(j).toFile).pacBioSystem
        c.copy(host = cfg.mailHost,
               port = cfg.mailPort.getOrElse(defaults.port),
               user = cfg.mailUser,
               password = cfg.mailPassword)
      })
      .text("JSON configuration file")
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  def run(c: SendTestEmailOptions): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()
    val status = SendTestEmail(c)
    val runTime = computeTimeDelta(JodaDateTime.now(), startedAt)
    status match {
      case Success(msg) =>
        println(msg)
        logger.info(msg)
        Right(ToolSuccess(toolId, runTime))
      case Failure(ex) =>
        println(ex.getMessage)
        logger.error(ex.getMessage)
        Left(ToolFailure(toolId, runTime, ex.getMessage))
    }
  }
}


object SendTestEmailApp extends App {
  import SendTestEmailTool._
  runner(args)
}
