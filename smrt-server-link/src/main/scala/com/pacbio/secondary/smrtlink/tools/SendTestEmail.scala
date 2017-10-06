package com.pacbio.secondary.smrtlink.tools

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import java.net.URL
import javax.mail.internet.InternetAddress

import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser
import spray.json._
import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.actors.SmrtLinkDalProvider
import com.pacbio.secondary.smrtlink.models.ConfigModels.{
  MailConfig,
  RootSmrtflowConfig
}
import com.pacbio.secondary.smrtlink.analysis.tools.{
  CommandLineToolRunner,
  ToolFailure,
  ToolSuccess
}
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.configloaders.{
  EngineCoreConfigLoader,
  PbsmrtpipeConfigLoader
}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.mail.{EmailTemplateResult, PbMailer}

case class SendTestEmailOptions(email: String,
                                host: Option[String],
                                port: Int,
                                user: Option[String],
                                password: Option[String])
    extends LoggerConfig

object SendTestEmail extends PbMailer {
  def apply(c: SendTestEmailOptions): Future[String] = {
    // FIXME. The host must be defined for the mailing to work.
    val templateResult = EmailTemplateResult(
      "SMRT Link Test Email",
      s"Successfully Sent Test email to ${c.email}")
    val toAddress = new InternetAddress(c.email)
    sender(templateResult,
           toAddress,
           mailConfig = MailConfig(c.host.get, c.port, c.user, c.password))
  }
}

object SendTestEmailTool
    extends CommandLineToolRunner[SendTestEmailOptions]
    with SmrtLinkDalProvider
    with SmrtLinkConfigProvider
    with EngineCoreConfigLoader
    with PbsmrtpipeConfigLoader {

  import com.pacbio.secondary.smrtlink.jsonprotocols.ConfigModelsJsonProtocol._

  override val VERSION = "0.1.0"
  override val toolId: String = "send_test_email"
  override val DESCRIPTION =
    """
      |Tool to test sending notification emails upon job completion.
    """.stripMargin

  val defaults = SendTestEmailOptions(null,
                                      mailHost(),
                                      mailPort(),
                                      mailUser(),
                                      mailPassword())

  def loadConfig(file: File): RootSmrtflowConfig = {
    FileUtils
      .readFileToString(file, "UTF-8")
      .parseJson
      .convertTo[RootSmrtflowConfig]
  }

  val parser = new OptionParser[SendTestEmailOptions]("send-test-email") {
    head(DESCRIPTION)
    arg[String]("email")
      .action({ (e, c) =>
        c.copy(email = e)
      })
      .text("Email address to send test message to (required)")
    opt[String]("host")
      .action({ (h, c) =>
        c.copy(host = Some(h))
      })
      .text(s"Mail server host name ${defaults.host.getOrElse("undefined")}")
    opt[Int]("port")
      .action({ (p, c) =>
        c.copy(port = p)
      })
      .text(s"SMTP port number ${defaults.port}")
    opt[String]("user")
      .action({ (u, c) =>
        c.copy(user = Some(u))
      })
      .text(s"SMTP login user ${defaults.user.getOrElse("undefined")}")
    opt[String]("password")
      .action({ (x, c) =>
        c.copy(password = Some(x))
      })
      .text(s"SMTP login password ${defaults.password.getOrElse("undefined")}")
    opt[String]("config-json")
      .action({ (j, c) =>
        val cfg = loadConfig(Paths.get(j).toFile).pacBioSystem
        c.copy(host = cfg.mailHost,
               port = cfg.mailPort.getOrElse(defaults.port),
               user = cfg.mailUser,
               password = cfg.mailPassword)
      })
      .text("JSON configuration file")
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  override def runTool(c: SendTestEmailOptions): Try[String] = {
    runAndBlock(SendTestEmail(c), 30.seconds)
  }

  def run(c: SendTestEmailOptions): Either[ToolFailure, ToolSuccess] =
    Left(ToolFailure(toolId, 0, "NOT Supported"))
}

object SendTestEmailApp extends App {
  import SendTestEmailTool._
  runnerWithArgsAndExit(args)
}
