package com.pacbio.secondary.smrttools.tools

import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrttools.client.ServiceAccessLayer

import akka.actor.ActorSystem
import org.joda.time.DateTime
import scopt.OptionParser
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.xml.XML

import java.net.URL
import java.util.UUID
import java.io.File


object Modes {
  sealed trait Mode {
    val name: String
  }
  case object STATUS extends Mode {val name = "status"}
  case object DATASET extends Mode {val name = "get-dataset"}
  case object IMPORT_DS extends Mode {val name = "import-dataset"}
  case object UNKNOWN extends Mode {val name = "unknown"}
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
                          datasetId: Int = 0,
                          path: File = null)


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
        c.copy(datasetId = i)
      } text "Dataset ID"
    ) text "Show dataset details"

    cmd(Modes.IMPORT_DS.name) action { (_, c) =>
      c.copy(command = (c) => println(c), mode = Modes.IMPORT_DS)
    } children(
      arg[File]("dataset-path") required() action { (p, c) =>
        c.copy(path = p)
      } text "DataSet XML path"
    ) text "Import DataSet XML"
  }
}


object PbServiceRunner extends LazyLogging {
  private def dsMetaTypeFromPath(path: String): String = {
    val ds = scala.xml.XML.loadFile(path)
    ds.attributes("MetaType").toString
  }

  private def dsUuidFromPath(path: String): UUID = {
    val ds = scala.xml.XML.loadFile(path)
    val uniqueId = ds.attributes("UniqueId").toString
    java.util.UUID.fromString(uniqueId)
  }

  def runStatus(sal: ServiceAccessLayer): Int = {
    val fx = for {
      status <- sal.getStatus
    } yield (status)

    val results = Await.result(fx, 5 seconds)
    val (status) = results
    println(status)
    0
  }

  def runGetDataSetInfo(sal: ServiceAccessLayer, datasetId: Int): Int = {
    val fx = for {
      dsInfo <- sal.getDataSetById(datasetId)
    } yield (dsInfo)

    val results = Await.result(fx, 5 seconds)
    val (dsInfo) = results
    println(dsInfo)
    0
  }

  def runGetJobInfo(sal: ServiceAccessLayer, jobId: UUID): Int = {
    val fx = for {
      jobInfo <- sal.getJobByUuid(jobId)
    } yield (jobInfo)
    val results = Await.result(fx, 5 seconds)
    val (jobInfo) = results
    println(jobInfo)
    0
  }

  // TODO refactor the dataset check so we can run it endlessly
  // (and move it to ServiceAccessLayer)
  def runImportDataSetSafe(sal: ServiceAccessLayer, path: String): Int = {
    val dsUuid = dsUuidFromPath(path)
    println(s"UUID: ${dsUuid.toString}")

    var xc = 0
    try {
      val haveDataSet = for {
        dsInfo <- sal.getDataSetByUuid(dsUuid)
      } yield (dsInfo)
      val results = Await.result(haveDataSet, 5 seconds)
      val (dsInfo) = results
      println(s"Dataset ${dsUuid.toString} already imported.")
      println(dsInfo)
    } catch {
      case ex => {
        println("Could not retrieve existing dataset record.")
        //println(ex.getMessage)
        xc = runImportDataSet(sal, path)
      }
    }
    xc
  }

  def runImportDataSet(sal: ServiceAccessLayer, path: String): Int = {
    val dsType = dsMetaTypeFromPath(path)
    logger.info(dsType)
    val fx2 = for {
      jobInfo <- sal.importDataSet(path, dsType)
    } yield (jobInfo)

    val results = Await.result(fx2, 5 seconds)
    val (jobInfo) = results
    //println(jobInfo)
    println("waiting for import job to complete...")
    sal.pollForJob(jobInfo.uuid)
    runGetJobInfo(sal, jobInfo.uuid)
  }

  def apply (c: PbService.CustomConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    val url = new URL(s"http://${c.host}:${c.port}")
    val sal = new ServiceAccessLayer(url)(actorSystem)
    val xc = c.mode match {
      case Modes.STATUS => runStatus(sal)
      case Modes.DATASET => runGetDataSetInfo(sal, c.datasetId)
      case Modes.IMPORT_DS => runImportDataSetSafe(sal, c.path.getAbsolutePath)
    }
    actorSystem.shutdown()
    xc
  }

}

object PbServiceApp extends App {
  def run(args: Seq[String]) = {
    val xc = PbService.parser.parse(args.toSeq, PbService.defaults) match {
      case Some(config) => PbServiceRunner(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
