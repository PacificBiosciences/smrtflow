
package com.pacbio.secondary.smrtserver.tools

//import com.pacbio.secondary.analysis.pipelines._
import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.reports.ReportModels
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
import scala.collection.immutable.Map
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
      cfgFile: File = null,
      ignoreTestFailures: Boolean = false,
      testJobId: Int = 0) extends LoggerConfig

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

    opt[Unit]("ignore-test-failures") action { (x, c) =>
      c.copy(ignoreTestFailures = true)
    } text "Exit 0 if pipeline job succeeds, regardless of test status"

    opt[Int]("only-tests") action { (i, c) =>
      c.copy(testJobId = i)
    } text "Just run tests on the specified (completed) job ID"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"
  }
}

class TestkitRunner(sal: AnalysisServiceAccessLayer) extends PbService(sal) {
  import AnalysisClientJsonProtocol._
  import ReportModels._

  protected def getPipelineId(pipelineXml: String): String = {
    val xmlData = scala.xml.XML.loadFile(pipelineXml)
    (xmlData \\ "pipeline-template-preset" \\ "import-template"  \ "@id").toString
  }

  private def testReportValue[T](actualValue: T, expectedValue: T, op: String)(implicit num: Numeric[T]): Boolean = op match { // is there a standard way to do this?
    case "eq" => num.equiv(actualValue, expectedValue)
    case "lt" => num.lt(actualValue, expectedValue)
    case "le" => num.lteq(actualValue, expectedValue)
    case "gt" => num.gt(actualValue, expectedValue)
    case "ge" => num.gteq(actualValue, expectedValue)
    case "ne" => !num.equiv(actualValue, expectedValue)
    case _ => false
  }

  // TODO return JUnit test cases
  private def testReportValues(jobId: Int, reportId: UUID, values: Map[String, JsValue]): Int = {
    Try {
      Await.result(sal.getAnalysisJobReport(jobId, reportId), TIMEOUT)
    } match {
      case Success(report) => {
        var rc = 0
        for ((k, v) <- values) {
          val keyFields = k.split("__")
          val attrId: String = keyFields(0)
          var op: String = "eq"
          if (keyFields.size == 2) op = keyFields(1)
          val isSameId = (other: String) => ((other != "") && (other.split('.').toList.last == attrId))
          var testStatus = 0
          // I'm writing this as an N^2 loop because it's much cleaner and
          // N will usually be single digits
          for (a <- report.attributes) {
            var testStatusAttr = a match {
              case ReportDoubleAttribute(id,name,value) => if (isSameId(id)) {
                val vExpected = v.asInstanceOf[JsNumber].value.toDouble
                if (testReportValue[Double](value, vExpected, op)) 1 else -1
              } else 0
              case ReportLongAttribute(id,name, value) => if (isSameId(id)) {
                val vExpected = v.asInstanceOf[JsNumber].value.toLong
                if (testReportValue[Long](value, vExpected, op)) 1 else -1
              } else 0
              case ReportStrAttribute(id,name,value) => 0
            }
            if (testStatusAttr != 0) testStatus = testStatusAttr
          }
          val testStr = s"${attrId} .${op}. ${v}"
          if (testStatus != 0) {
            println(if (testStatus == -1) s"failed:${testStr}" else s"passed:${testStr}")
            rc = max(rc, max(-testStatus, 0))
          }
        }
        rc
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  def runTests(testValuesPath: String, jobId: Int): Int = {
    val jsonSrc = Source.fromFile(testValuesPath).getLines.mkString
    val jsonAst = jsonSrc.parseJson
    jsonAst match {
      case JsObject(x) => {
      for ((k,v) <- x) {
        if (k == "reports") {
          v match {
            case JsObject(xx) => {
              val reports = Try {
                Await.result(sal.getAnalysisJobReports(jobId), TIMEOUT)
              } match {
                case Success(r) => r
                case Failure(err) => Seq[DataStoreReportFile]()
              }
              val reportsMap = (for (r <- reports) yield (r.dataStoreFile.sourceId.split("-")(0), r.dataStoreFile.uuid)).toMap
              var rc = 0
              for ((rk,rv) <- xx) {
                rc = max(rc, rv match {
                  case JsObject(v) => {
                    val reportId = reportsMap(rk)
                    max(rc, testReportValues(jobId, reportId, v))
                  }
                  case _ => errorExit(s"Can't unmarshal ${rv}")
                })
              }
              rc
            }
            case _ => return errorExit("Can't process this JSON file - 'reports' section must be a dict")
          }
        }
      }
      }
      case _ => return errorExit("Can't process this JSON file")
    }
    0
  }

  def runTestkitCfg(cfg: File, skipTests: Boolean = false,
                    ignoreTestFailures: Boolean = false): Int = {
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
    val presets = getPipelinePresets(new File(presetXml))
    val pipelineOptions = getPipelineServiceOptions(title, pipelineId,
                                                    entryPoints, presets)
    var jobId = 0
    xc = Try {
      Await.result(sal.runAnalysisPipeline(pipelineOptions), TIMEOUT)
    } match {
      case Success(jobInfo) => {
        println(s"Job ${jobInfo.uuid} started")
        printJobInfo(jobInfo)
        jobId = jobInfo.id
        waitForJob(jobInfo.uuid)
      }
      case Failure(err) => errorExit(err.getMessage)
    }
    if ((xc == 0) && (! skipTests)) {
      val testValues = Option(ini.get("pbsmrtpipe:pipeline", "test_values")).getOrElse("")
      if (testValues != "") {
        var testStatus = runTests(testValues, jobId)
        if (ignoreTestFailures) 0 else testStatus
      } else {
        println("No test_values JSON defined, skipping tests")
        xc
      }
    } else xc
  }

  def runTestsOnly(cfg: File, testJobId: Int,
                    ignoreTestFailures: Boolean = false): Int = {
    if (runGetJobInfo(Left(testJobId)) != 0) return errorExit(s"Couldn't retrieve job ${testJobId}")
    val ini = new Ini(cfg)
    val testValues = Option(ini.get("pbsmrtpipe:pipeline", "test_values")).getOrElse("")
    if (testValues != "") {
      var testStatus = runTests(testValues, testJobId)
      if (ignoreTestFailures) 0 else testStatus
    } else errorExit("No tests defined")
  }
}

object TestkitRunner {
  def apply (c: TestkitParser.TestkitConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    val url = new URL(s"http://${c.host}:${c.port}")
    val sal = new AnalysisServiceAccessLayer(url)(actorSystem)
    val tk = new TestkitRunner(sal)
    var xc = if (c.testJobId > 0) tk.runTestsOnly(c.cfgFile, c.testJobId, c.ignoreTestFailures) else tk.runTestkitCfg(c.cfgFile)
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
