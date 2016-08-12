package com.pacbio.common.tools

import com.pacbio.common.client._

import java.net.URL

import akka.actor.ActorSystem
import org.joda.time.DateTime
import scopt.OptionParser
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.util.Try


// TODO get defaults from prod.conf
case class GetStatusConfig(
    host: String = "http://localhost",
    port: Int = 8070,
    uiPort: Int = -1, // optional
    sleepTime: Int = 5,
    maxRetries: Int = 3) extends LoggerConfig

/**
 * Get the status of SMRT services
 *
 */
trait GetStatusParser {
  final val TOOL_ID = "pbscala.tools.get_status"
  final val VERSION = "0.1.0"
  final val DEFAULT = GetStatusConfig("http://localhost", 8070)

  lazy val parser = new OptionParser[GetStatusConfig]("get-status") {
    head("Get SMRT server status ", VERSION)
    note("Tool to check the status of a currently running server")

    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of smrt server"

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on smrt server"

    opt[Int]("ui-port") action { (x, c) =>
      c.copy(uiPort = x)
    } text "UI port on smrt server"

    opt[Int]("max-retries") action { (x, c) =>
      c.copy(maxRetries = x)
    } text "Number of retries"

    opt[Int]("sleep-time") action { (x, c) =>
      c.copy(sleepTime = x)
    } text "Sleep time between retries (in seconds)"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }
}

trait GetSmrtServerStatus extends LazyLogging {
  val STATUS_TIMEOUT = 5 seconds

  def getStatus(sal: ServiceAccessLayer, maxRetries: Int, sleepTime: Int): Int = {
    var xc = 1
    var ntries = 0
    while (ntries < maxRetries) {
      ntries += 1
      val result = Try { Await.result(sal.getStatus, STATUS_TIMEOUT) }
      result match {
        case Success(x) => {
          println(s"GET ${sal.baseUrl}: SUCCESS")
          println(x)
          ntries = maxRetries
          xc = 0
        }
        case Failure(err) => {
          println(s"failed: ${err.getMessage}")
          if (ntries < maxRetries) {
            Thread.sleep(sleepTime * 1000)
          }
        }
      }
    }
    xc
  }
}


object GetStatusRunner extends GetSmrtServerStatus with LazyLogging {
  final val SERVICE_ENDPOINTS = Vector()

  def apply (c: GetStatusConfig): Int = {
    val startedAt = DateTime.now()

    implicit val actorSystem = ActorSystem("get-status")
    val url = new URL(s"http://${c.host}:${c.port}")
    println(s"URL: ${url}")
    val sal = new ServiceAccessLayer(url)(actorSystem)
    var xc = getStatus(sal, c.maxRetries, c.sleepTime)
    if (xc == 0) {
      if (c.uiPort > 0) xc = sal.checkUiEndpoint(c.uiPort) else println("No UI port specified, skipping")
      if (xc == 0) xc = sal.checkServiceEndpoints
    }

    logger.debug("shutting down actor system")
    actorSystem.shutdown()
    xc
  }
}

object GetStatusApp extends App with GetStatusParser {
  def run(args: Seq[String]) = {
    val exitCode = parser.parse(args, DEFAULT) match {
      case Some(opts) => GetStatusRunner(opts)
      case _ => 1
    }
    println(s"Exiting $TOOL_ID v$VERSION with exit code $exitCode")
    sys.exit(exitCode)
  }

  run(args)
}
