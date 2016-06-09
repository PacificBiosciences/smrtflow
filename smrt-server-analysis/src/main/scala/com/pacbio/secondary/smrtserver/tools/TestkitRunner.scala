
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
import scala.xml._
import scala.io.Source
import scala.math._

import java.net.URL
import java.util.UUID
import java.io.{File, FileReader, PrintWriter}


import com.pacbio.logging.{LoggerConfig, LoggerOptions}

object TestkitParser {
  val VERSION = "0.1.0"
  val TOOL_ID = "pbscala.tools.pbtestkit-service-runner"

  case class TestkitConfig(
      host: String,
      port: Int,
      cfgFile: File = null,
      xunitOut: File = null,
      ignoreTestFailures: Boolean = false,
      testJobId: Int = 0) extends LoggerConfig

  lazy val defaults = TestkitConfig("localhost", 8070, null, xunitOut=new File("test-output.xml"))

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

    opt[File]("xunit") action { (f, c) =>
      c.copy(xunitOut = f)
    } text "Output XUnit test results"

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

  protected val testCases = ArrayBuffer[scala.xml.Elem]()
  protected var nFailures = 0
  protected var nErrors = 0
  protected var nSkips = 0
  protected var nPassed = 0
  protected def nTests: Int = testCases.size

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

  sealed trait TestResult {
    val mode: String
  }

  private case object TestSkipped extends TestResult {val mode = "skipped"}
  private case object TestFailed extends TestResult {val mode="failed"}
  private case object TestPassed extends TestResult {val mode="passed"}

  // TODO return JUnit test cases
  private def testReportValues(jobId: Int, reportId: UUID, values: Map[String, JsValue]): Int = {
    Try {
      Await.result(sal.getAnalysisJobReport(jobId, reportId), TIMEOUT)
    } match {
      case Success(report) => {
        (for ((k, v) <- values) yield {
          val keyFields = k.split("__")
          val attrId: String = keyFields(0)
          var op: String = "eq"
          if (keyFields.size == 2) op = keyFields(1)
          val isSameId = (other: String) => ((other != "") && (other.split('.').toList.last == attrId))
          var testStatus: TestResult = TestSkipped
          // I'm writing this as an N^2 loop because it's much cleaner and
          // N will usually be single digits
          for (a <- report.attributes) {
            var testStatusAttr: TestResult = a match {
              case ReportDoubleAttribute(id,name,value) => if (isSameId(id)) {
                val vExpected = v.asInstanceOf[JsNumber].value.toDouble
                if (testReportValue[Double](value, vExpected, op)) TestPassed else TestFailed
              } else TestSkipped
              case ReportLongAttribute(id,name, value) => if (isSameId(id)) {
                val vExpected = v.asInstanceOf[JsNumber].value.toLong
                if (testReportValue[Long](value, vExpected, op)) TestPassed else TestFailed
              } else TestSkipped
              case ReportStrAttribute(id,name,value) => TestSkipped
            }
            testStatus = testStatusAttr match {
              case TestSkipped => testStatus
              case x => x
            }
          }
          val testStr = s"${attrId} .${op}. ${v}"
          val testClass = "Test" + (for (word <- report.id.split('.').last.split("_")) yield {word.capitalize}).toList.mkString("")
          val testName = s"test_${attrId}"
          var testCase = testStatus match {
            case TestPassed => {
              nPassed += 1
              <testcase classname={testClass} name={testName} time="0"/>
            }
            case TestFailed => {
              nFailures += 1
              <testcase classname={testClass} name={testName} time="0"><failure message={s"FAILED: ${attrId}"}>{testStr}</failure></testcase>
            }
            case TestSkipped => {
              nSkips += 1
              <testcase classname={testClass} name={testName} time="0"><skipped/></testcase>
            }
          }
          testCases.append(testCase)
          println(s"${testStatus.mode}:${testStr}")
          testStatus match {
            case TestFailed => 1
            case _ => 0
          }
        }).toList.max
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  def runTests(testValuesPath: String, jobId: Int): Int = {
    val jsonSrc = Source.fromFile(testValuesPath).getLines.mkString
    val jsonAst = jsonSrc.parseJson
    jsonAst match {
      case JsObject(x) => (for ((k,v) <- x) yield {
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
              (for ((rk,rv) <- xx) yield {
                rv match {
                  case JsObject(v) => {
                    val reportId = reportsMap(rk)
                    testReportValues(jobId, reportId, v)
                  }
                  case _ => errorExit(s"Can't unmarshal ${rv}")
                }
              }).toList.max
            }
            case _ => errorExit("Can't process this JSON file - 'reports' section must be a dict")
          }
        } else -1
      }).toList.max
      case _ => errorExit("Can't process this JSON file")
    }
  }

  def writeTestResults(xunitOut: String): Unit = {
    val testSuite = <testsuite tests={nTests.toString} failures={nFailures.toString} skips={nSkips.toString} errors={nErrors.toString}>{for (t <- testCases) yield t}</testsuite>
    val pw = new PrintWriter(new File(xunitOut))
    try pw.write(testSuite.toString) finally pw.close()
  }

  def runTestkitCfg(cfg: File, xunitOut: File, skipTests: Boolean = false,
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
        writeTestResults(xunitOut.getAbsolutePath)
        if (ignoreTestFailures) 0 else testStatus
      } else {
        println("No test_values JSON defined, skipping tests")
        xc
      }
    } else xc
  }

  def runTestsOnly(cfg: File, testJobId: Int, xunitOut: File,
                    ignoreTestFailures: Boolean = false): Int = {
    if (runGetJobInfo(Left(testJobId)) != 0) return errorExit(s"Couldn't retrieve job ${testJobId}")
    val ini = new Ini(cfg)
    val testValues = Option(ini.get("pbsmrtpipe:pipeline", "test_values")).getOrElse("")
    if (testValues != "") {
      var testStatus = runTests(testValues, testJobId)
      writeTestResults(xunitOut.getAbsolutePath)
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
    try {
      if (c.testJobId > 0) {
        tk.runTestsOnly(c.cfgFile, c.testJobId, c.xunitOut, c.ignoreTestFailures)
      } else tk.runTestkitCfg(c.cfgFile, c.xunitOut)
    } finally {
      actorSystem.shutdown()
    }
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
