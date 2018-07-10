package com.pacbio.secondary.smrtlink.tools

import akka.actor.ActorSystem
import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.analysis.tools._
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceClient
import com.pacbio.secondary.smrtlink.models.{
  EulaRecord,
  EulaUpdateRecord,
  PacBioComponentManifest
}
import scopt.OptionParser

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

case class AcceptUserAgreementConfig(host: String = "http://localhost",
                                     port: Int = 8070,
                                     user: String =
                                       System.getProperty("user.name"),
                                     update: Boolean = false,
                                     enableInstallMetrics: Boolean = true,
                                     enableJobMetrics: Boolean = true)
    extends LoggerConfig

object AcceptUserAgreement
    extends CommandLineToolRunner[AcceptUserAgreementConfig]
    with ConfigLoader {
  final val TIMEOUT = 10 seconds
  final val SMRTLINK_SYSTEM_ID = "smrtlink"

  val toolId = "pbscala.tools.accept_user_agreement"
  val VERSION = "0.2.0"
  val DESCRIPTION = "PacBio SMRTLink User Agreement Acceptance Tool"
  lazy val defaultHost: String = Try {
    conf.getString("smrtflow.server.dnsName")
  }.getOrElse("localhost")
  lazy val defaultPort: Int = conf.getInt("smrtflow.server.port")
  lazy val defaults = AcceptUserAgreementConfig(defaultHost, defaultPort)

  lazy val parser =
    new OptionParser[AcceptUserAgreementConfig]("accept-user-agreement") {
      head(DESCRIPTION, VERSION)

      opt[String]("host") action { (x, c) =>
        c.copy(host = x)
      } text s"Hostname of SMRT Link server (default: ${defaults.host})"

      opt[Int]("port") action { (x, c) =>
        c.copy(port = x)
      } text s"Services port on SMRT Link server (default: ${defaults.port})"

      opt[String]("user") action { (x, c) =>
        c.copy(user = x)
      } text s"User name to save in acceptance record (default: ${defaults.user})"

      opt[Boolean]("job-metrics") action { (x, c) =>
        c.copy(enableJobMetrics = x)
      } text s"Enable Job Metrics (default: ${defaults.enableJobMetrics})"

      opt[Boolean]("install-metrics") action { (x, c) =>
        c.copy(enableInstallMetrics = x)
      } text s"Enable Install Metrics (default: ${defaults.enableInstallMetrics})"

      opt[Boolean]("update") action { (x, c) =>
        c.copy(update = x)
      } text s"Update configuration of previously accepted Eula Metrics (default: ${defaults.update})"

      opt[Unit]("version") action { (x, c) =>
        showVersion
        sys.exit(0)
      } text "Show tool version and exit"

      opt[Unit]('h', "help") action { (x, c) =>
        showUsage
        sys.exit(0)
      } text "Show Options and exit"

      LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
    }

  def getSmrtLinkSystemVersion(
      ms: Seq[PacBioComponentManifest]): Future[String] = {
    ms.sortWith(_.id > _.id)
      .find(m => m.id == SMRTLINK_SYSTEM_ID)
      .map(v => Future.successful(v.version))
      .getOrElse(Future.failed(
        throw new Exception("Can't determine SMRT Link version")))
  }

  def getOrAcceptEula(client: SmrtLinkServiceClient,
                      user: String,
                      smrtLinkVersion: String,
                      enableInstallMetrics: Boolean,
                      enableJobMetrics: Boolean): Future[EulaRecord] = {
    client
      .getEula(smrtLinkVersion)
      .recoverWith {
        case NonFatal(_) =>
          client.acceptEula(user, enableInstallMetrics, enableJobMetrics)
      }
  }

  def runAcceptEula(client: SmrtLinkServiceClient,
                    user: String,
                    enableInstallMetrics: Boolean,
                    enableJobMetrics: Boolean): Future[EulaRecord] = {
    for {
      manifests <- client.getPacBioComponentManifests
      smrtLinkVersion <- getSmrtLinkSystemVersion(manifests)
      eula <- getOrAcceptEula(client,
                              user,
                              smrtLinkVersion,
                              enableInstallMetrics,
                              enableJobMetrics)
    } yield eula
  }

  def runUpdateEulaMetrics(client: SmrtLinkServiceClient,
                           enableInstallMetrics: Boolean,
                           enableJobMetrics: Boolean): Future[EulaRecord] = {
    for {
      manifests <- client.getPacBioComponentManifests
      smrtLinkVersion <- getSmrtLinkSystemVersion(manifests)
      updatedEula <- client.updateEula(
        smrtLinkVersion,
        EulaUpdateRecord(Some(enableInstallMetrics), Some(enableJobMetrics)))
    } yield updatedEula
  }

  override def runTool(c: AcceptUserAgreementConfig): Try[String] = {

    implicit val actorSystem = ActorSystem("accept-eula")

    val client = new SmrtLinkServiceClient(c.host, c.port)(actorSystem)

    val fx: Future[String] = if (c.update) {
      runUpdateEulaMetrics(client, c.enableInstallMetrics, c.enableJobMetrics)
        .map(e => s"Updated Eula $e")
    } else {
      runAcceptEula(client, c.user, c.enableInstallMetrics, c.enableJobMetrics)
        .map(e => s"Accepted Eula $e")
    }

    fx.onComplete { _ =>
      actorSystem.terminate()
    }

    runAndBlock(fx, TIMEOUT)
  }

  // Legacy interface
  def run(c: AcceptUserAgreementConfig) =
    Left(ToolFailure(toolId, 1, "NOT SUPPORTED"))
}

object AcceptUserAgreementApp extends App {
  import AcceptUserAgreement._
  runnerWithArgsAndExit(args)
}
