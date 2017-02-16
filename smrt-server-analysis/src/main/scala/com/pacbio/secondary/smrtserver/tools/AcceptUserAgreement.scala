
package com.pacbio.secondary.smrtserver.tools

import java.net.URL

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps

import akka.actor.ActorSystem
import scopt.OptionParser

import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer


case class AcceptUserAgreementConfig(
    host: String = "http://localhost",
    port: Int = 8070,
    user: String = System.getProperty("user.name"))

trait AcceptUserAgreementParser {
  final val TOOL_ID = "pbscala.tools.accept_user_agreement"
  final val VERSION = "0.1.0"
  final val DEFAULT = AcceptUserAgreementConfig()

  lazy val parser = new OptionParser[AcceptUserAgreementConfig]("accept-user-agreement") {

    head("PacBio SMRTLink User Agreement Acceptance Tool", VERSION)

    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of SMRT Link server"

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on SMRT Link server"

    opt[String]("user") action { (x, c) =>
      c.copy(user = x)
    } text "User name to save in acceptance record"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"
  }
}

object AcceptUserAgreementApp extends App with AcceptUserAgreementParser {
  final val TIMEOUT = 10 seconds

  def acceptUserAgreement(c: AcceptUserAgreementConfig) = {
    implicit val actorSystem = ActorSystem("get-status")
    val url = new URL(s"http://${c.host}:${c.port}")
    println(s"URL: ${url}")
    val sal = new AnalysisServiceAccessLayer(url)(actorSystem)
    val manifest = Await.result(sal.getPacBioComponentManifests, TIMEOUT)
    val version = manifest.sortWith(_.id > _.id).find(
      m => m.id == "smrtlink-analysisservices-gui" || m.id == "pacbio.services.eula").getOrElse(
      throw new RuntimeException("Can't determine SMRT Link version")).version
    Try {
      Await.result(sal.getEula(version), TIMEOUT)
    } match {
      case Success(eula) =>
        println(s"Skipping - SMRT Link user agreement for version $version was already accepted by ${eula.user} on ${eula.acceptedAt}")
      case Failure(x) =>
        val eula = Await.result(sal.acceptEula(c.user, version), TIMEOUT)
        println(s"SMRT Link user agreement for version $version accepted by ${eula.user} on ${eula.acceptedAt}")
    }
    0
  }

  def run(args: Seq[String]) = {
    val exitCode = parser.parse(args, DEFAULT) match {
      case Some(opts) => Try {
        acceptUserAgreement(opts)
      } match {
        case Success(rc) => rc
        case Failure(err) => println(s"ERROR: $err"); 1
      }
      case _ => 1
    }
    println(s"Exiting $TOOL_ID v$VERSION with exit code $exitCode")
    sys.exit(exitCode)
  }

  run(args)
}
