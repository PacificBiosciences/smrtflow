package com.pacbio.secondary.smrttools.tools

import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrttools.client.ServiceAccessLayer

import java.net.URL

import akka.actor.ActorSystem
import org.joda.time.DateTime
import scopt.OptionParser
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

 
import scala.util.Try


case class GetStatusConfig(host: String = "http://localhost",
                           port: Int = 8070,
                           debug: Boolean = false,
                           sleepTime: Int = 5,
                           maxRetries: Int = 3)

/*
 * Get the status of SMRTLink services
 *
 */

trait GetStatusParser {
  final val TOOL_ID = "pbscala.tools.get_status"
  final val VERSION = "0.1.0"
  final val DEFAULT = GetStatusConfig("http://localhost", 8070, debug = false)

  lazy val parser = new OptionParser[GetStatusConfig]("get-status") {
    head("Get SMRTLink status ", VERSION)
    note("Tool to check the status of a currently running smrtlink server")

    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of smrtlink server"

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on smrtlink server"

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
  }
}

object GetStatusRunner extends LazyLogging {

  def apply (c: GetStatusConfig): Int = {
    val startedAt = DateTime.now()

    implicit val actorSystem = ActorSystem("get-status")
    val url = new URL(s"http://${c.host}:${c.port}")
    println(s"URL: ${url}")
    val sal = new ServiceAccessLayer(url)(actorSystem)
    var xc = 1
    var ntries = 0
    while (ntries < c.maxRetries) {
      ntries += 1
      val result = Try { Await.result(sal.getStatus, 5 seconds) }
      result match {
        case Success(x) => {
          println(s"GET ${url}: SUCCESS")
          println(x)
          ntries = c.maxRetries
          xc = 0
        }
        case Failure(err) => {
          println(s"failed: ${err}")
          if (ntries < c.maxRetries) {
            Thread.sleep(c.sleepTime * 1000)
          }
        }
      }
    }
    if (xc == 0) {
      // FIXME this crashes with servers that have many jobs
      val result = Try { Await.result(sal.getAnalysisJobs, 20 seconds) }
      result match {
        case Success(x) => {
          println(s"${x.size} analysis jobs found")
        }
        case Failure(err) => {
          println(s"failed to retrieve analysis jobs")
          println(s"${err}")
          xc = 1
        }
      }
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
