
package com.pacbio.secondary.smrtserver.tools

import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtserver.client.{AnalysisServiceAccessLayer,AnalysisClientJsonProtocol}
import com.pacbio.secondary.smrtlink.models.{BoundServiceEntryPoint, PbSmrtPipeServiceOptions, ServiceTaskOptionBase}

import org.ini4j._
import akka.actor.ActorSystem
import org.joda.time.DateTime
import scopt.OptionParser
import com.typesafe.scalalogging.LazyLogging
import spray.httpx
import spray.json._
import spray.httpx.SprayJsonSupport


import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.mutable._
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.xml.XML
import scala.io.Source
import scala.math._

import java.net.URL
import java.util.UUID
import java.io.{File, FileReader}


import com.pacbio.logging.{LoggerConfig, LoggerOptions}

object TestkitParser {
  val VERSION = "0.1.0"
  val TOOL_ID = "pbscala.tools.pbtestkit-service-runner"

  case class TestkitConfig(
      host: String,
      port: Int,
      cfgFile: File = null) extends LoggerConfig

  lazy val defaults = TestkitConfig("localhost", 8070, null)

  lazy val parser = new OptionParser[TestkitConfig]("pbtestkit-service-runner") {
    head("Test runner for pbsmrtpipe jobs", VERSION)
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of smrtlink server"

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on smrtlink server"

    arg[File]("testkit-cfg") required() action { (p, c) =>
      c.copy(cfgFile = p)
    } text "testkit.cfg file"
  }
}

class TestkitRunner(sal: AnalysisServiceAccessLayer) extends PbService(sal) {
  import AnalysisClientJsonProtocol._

  protected def importEntryPoint(eid: String, xmlPath: String): BoundServiceEntryPoint = {
    var dsType = dsMetaTypeFromPath(xmlPath)
    var dsUuid = dsUuidFromPath(xmlPath)
    var xc = runImportDataSetSafe(xmlPath)
    if (xc != 0) throw new Exception(s"Could not import dataset ${eid}:${xmlPath}")
    // this is stupidly inefficient
    val dsId = Try {
      Await.result(sal.getDataSetByUuid(dsUuid), TIMEOUT)
    } match {
      case Success(ds) => ds.id
      case Failure(err) => throw new Exception(err.getMessage)
    }
    BoundServiceEntryPoint(eid, dsType, dsId)
  }

  protected def getPipelineId(pipelineXml: String): String = {
    val xmlData = scala.xml.XML.loadFile(pipelineXml)
    (xmlData \\ "pipeline-template-preset" \\ "import-template"  \ "@id").toString
  }

  def runTestkitCfg(cfg: File): Int = {
    val ini = new Ini(cfg)
    val title = ini.get("pbsmrtpipe:pipeline", "id")
    val pipelineXml = ini.get("pbsmrtpipe:pipeline", "pipeline_xml")
    val presetXml = ini.get("pbsmrtpipe:pipeline", "preset_xml")
    var xc = 0
    println(title)
    println("Importing entry points...")
    val entryPoints = new ArrayBuffer[BoundServiceEntryPoint]
    for ((eid,xmlPath) <- ini.get("entry_points")) {
      xc = max(xc, Try { importEntryPoint(eid, xmlPath) } match {
        case Success(ep) => {
          entryPoints.append(ep)
          println(ep)
          0
        }
        case Failure(err) => errorExit(s"Could not load entry point ${eid}")
      })
    }
    if (xc != 0) return errorExit("fatal error, exiting")
    val pipelineId = getPipelineId(pipelineXml)
    val taskOptions = Seq[ServiceTaskOptionBase]()
    val workflowOptions = Seq[ServiceTaskOptionBase]()
    val pipelineOptions = PbSmrtPipeServiceOptions(
      title, pipelineId, entryPoints, taskOptions, workflowOptions)
    xc = runAnalysisPipelineImpl(pipelineOptions, validate=false)
    xc
  }
}

object TestkitRunner {
  def apply (c: TestkitParser.TestkitConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    val url = new URL(s"http://${c.host}:${c.port}")
    val sal = new AnalysisServiceAccessLayer(url)(actorSystem)
    val tk = new TestkitRunner(sal)
    val xc = tk.runTestkitCfg(c.cfgFile)
    actorSystem.shutdown()
    xc
  }
}

object TestkitRunnerApp extends App {
  def run(args: Seq[String]) = {
    val xc = TestkitParser.parser.parse(args.toSeq, TestkitParser.defaults) match {
      case Some(config) => TestkitRunner(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
