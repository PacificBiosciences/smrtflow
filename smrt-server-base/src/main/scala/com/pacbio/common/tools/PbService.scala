package com.pacbio.common.tools

import akka.actor.ActorSystem
import org.joda.time.DateTime
import scopt.OptionParser
import com.typesafe.scalalogging.LazyLogging
import spray.httpx
import spray.json._
import spray.httpx.SprayJsonSupport


import scala.collection.mutable
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.xml.XML
import scala.io.Source

import java.net.URL

import com.pacbio.common.client._
import com.pacbio.logging.{LoggerConfig, LoggerOptions}


trait PbServiceBase {
  object Modes {
    sealed trait Mode {
      val name: String
    }
    case object STATUS extends Mode {val name = "status"}
    case object UNKNOWN extends Mode {val name = "unknown"}
  }
  
  case class PbServiceConfig(
      var mode: Modes.Mode = Modes.UNKNOWN,
      var host: String,
      var port: Int,
      var command: PbServiceConfig => Unit) extends LoggerConfig
  
  trait BaseArgsParser { self: OptionParser[PbServiceConfig] =>
    head("PacBio SMRTLink Services Client", "0.1")
  
    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of smrtlink server"
  
    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on smrtlink server"
  
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }
  
  trait StatusParser { self: OptionParser[PbServiceConfig] =>
    cmd(Modes.STATUS.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.STATUS)
    }
  }

  def showDefaults(c: PbServiceConfig): Unit = {
    println(s"Defaults $c")
  }

  protected val TIMEOUT = 10 seconds

  // FIXME this is crude
  protected def errorExit(msg: String): Int = {
    println(msg)
    1
  }

  def runStatus(sal: ServiceAccessLayer): Int = {
    Try { Await.result(sal.getStatus, TIMEOUT) } match {
      case Success(status) => {
        println(s"Status: ${status.message}")
        0
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }
}

object PbService extends PbServiceBase {
  val VERSION = "0.1"
  lazy val defaults = PbServiceConfig(null, "localhost", 8070, showDefaults)
  lazy val parser = new OptionParser[PbServiceConfig]("pbservice") with BaseArgsParser with StatusParser {
    head("PacBio SMRTLink Services Client", VERSION)
  }

  def apply (c: PbServiceConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    val url = new URL(s"http://${c.host}:${c.port}")
    val sal = new ServiceAccessLayer(url)(actorSystem)
    val xc = c.mode match {
      case Modes.STATUS => runStatus(sal)
      case _ => errorExit("Unsupported action")
    }
    actorSystem.shutdown()
    xc
  }
}

object PbServiceApp extends App {
  def run(args: Seq[String]) = {
    val xc = PbService.parser.parse(args.toSeq, PbService.defaults) match {
      case Some(config) => PbService(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
