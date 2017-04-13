
package com.pacbio.secondary.smrtlink.tools

import akka.actor.ActorSystem
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


case class AcceptUserAgreementConfig(
    host: String = "http://localhost",
    port: Int = 8070,
    user: String = System.getProperty("user.name")) extends LoggerConfig

object AcceptUserAgreement extends CommandLineToolRunner[AcceptUserAgreementConfig] {
  final val TIMEOUT = 10 seconds
  val toolId = "pbscala.tools.accept_user_agreement"
  val VERSION = "0.1.0"
  val DESCRIPTION = "PacBio SMRTLink User Agreement Acceptance Tool"
  lazy val conf = ConfigFactory.load()
  lazy val defaultHost: String = Try { conf.getString("smrtflow.server.dnsName") }.getOrElse("localhost")
  lazy val defaultPort: Int = conf.getInt("smrtflow.server.port")
  lazy val defaults = AcceptUserAgreementConfig(defaultHost, defaultPort)

  lazy val parser = new OptionParser[AcceptUserAgreementConfig]("accept-user-agreement") {
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

  def acceptUserAgreement(c: AcceptUserAgreementConfig) = {
    implicit val actorSystem = ActorSystem("get-status")
    val sal = new SmrtLinkServiceAccessLayer(c.host, c.port)(actorSystem)
    println(s"URL: ${sal.baseUrl}")
    val manifest = Await.result(sal.getPacBioComponentManifests, TIMEOUT)
    val version = manifest.sortWith(_.id > _.id).find(
      m => m.id == "smrtlink-analysisservices-gui" || m.id == "pacbio.services.eula").getOrElse(
      throw new RuntimeException("Can't determine SMRT Link version")).version
    Try {
      Await.result(sal.getEula(version), TIMEOUT)
    } match {
      case Success(eula) =>
        println(s"Skipping - SMRT Link user agreement for version $version was already accepted by ${eula.user} on ${eula.acceptedAt}")
      case Failure(_) =>
        val eula = Await.result(sal.acceptEula(c.user), TIMEOUT)
        println(s"SMRT Link user agreement for version $version accepted by ${eula.user} on ${eula.acceptedAt}")
    }
    0
  }

  def run(c: AcceptUserAgreementConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()
    Try { acceptUserAgreement(c) } match {
      case Success(rc) => Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
      case Failure(err) =>
        Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), err.getMessage))
    }
  }
}

object AcceptUserAgreementApp extends App {
  import AcceptUserAgreement._
  runner(args)
}
