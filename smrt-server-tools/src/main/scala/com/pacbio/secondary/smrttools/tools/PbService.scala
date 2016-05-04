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


object Modes {
  sealed trait Mode {
    val name: String
  }
  case object STATUS extends Mode {val name = "status"}
  case object DATASET extends Mode {val name = "dataset"}
  case object UNKNOWN extends Mode { val name = "unknown"}
}

object PbService {
  val VERSION = "0.1.0"
  var TOOL_ID = "pbscala.tools.pbservice"

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  case class CustomConfig(mode: Modes.Mode = Modes.UNKNOWN,
                          host: String,
                          port: Int,
                          debug: Boolean = false,
                          command: CustomConfig => Unit = showDefaults,
                          dataset_id: Int = 0)


  lazy val defaults = CustomConfig(null, "localhost", 8070, debug=false)

  lazy val parser = new OptionParser[CustomConfig]("pbservice") {
    head("PacBio SMRTLink Services Client", VERSION)

    opt[Boolean]("debug") action { (v,c) =>
      c.copy(debug=true)
    } text "Debug mode"
    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of smrtlink server"

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on smrtlink server"

    cmd(Modes.STATUS.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.STATUS)
    }

    cmd(Modes.DATASET.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.DATASET)
    } children(
      arg[Int]("dataset-id") required() action { (i, c) =>
        c.copy(dataset_id = i)
      } text "Dataset ID"
    ) text "Show dataset details"
  }
}


object PbServiceApp extends App {

  def runStatus(sal: ServiceAccessLayer) {
    val fx = for {
      status <- sal.getStatus
    } yield (status)

    val results = Await.result(fx, 5 seconds)
    val (status) = results
    println(status)
  }

  def runGetDataSetInfo(sal: ServiceAccessLayer, dataset_id: Int) {
    val fx = for {
      ds_info <- sal.getDataSetById(dataset_id)
    } yield (ds_info)

    val results = Await.result(fx, 5 seconds)
    val (ds_info) = results
    println(ds_info)
  }

  override def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem("get-status")
    val xs = PbService.parser.parse(args.toSeq, PbService.defaults) map { c =>
        val url = new URL(s"http://${c.host}:${c.port}")
        val sal = new ServiceAccessLayer(url)(actorSystem)
        c.mode match {
          case Modes.STATUS => runStatus(sal)
          case Modes.DATASET => runGetDataSetInfo(sal, c.dataset_id)
        }
    }
    actorSystem.shutdown()
  }
}
