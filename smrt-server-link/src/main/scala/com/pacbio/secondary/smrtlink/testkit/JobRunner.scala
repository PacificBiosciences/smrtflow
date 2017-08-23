
package com.pacbio.secondary.smrtlink.testkit

import java.io.{File, PrintWriter}
import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.common.models._
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.tools.PbService
import scopt.OptionParser
import spray.json._

import scala.collection.immutable.Seq
import scala.collection.mutable._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Source
import scala.language.postfixOps
import scala.math._
import scala.util.{Failure, Success, Try}

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

    opt[Unit]("emit-json") action { (i, c) =>
      MockConfig.showCfg
      sys.exit(0)
    } text "Display an example config and exit"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"
  }
}

class TestkitRunner(sal: SmrtLinkServiceAccessLayer) extends PbService(sal, 30.minutes) with TestkitJsonProtocol {
  import CommonModelImplicits._
  import ReportModels._
  import TestkitModels._

  protected val testCases = ArrayBuffer[scala.xml.Elem]()
  protected var nFailures = 0
  protected var nErrors = 0
  protected var nSkips = 0
  protected var nPassed = 0
  protected def nTests: Int = testCases.size

  protected def getPipelineId(pipelineXml: Path): String = {
    val xmlData = scala.xml.XML.loadFile(pipelineXml.toFile)
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

  private def makeTestPassed(testClass: String, testName: String, time: Int = 0): scala.xml.Elem = {
    nPassed += 1
    <testcase classname={testClass} name={testName} time={s"$time"}/>
  }

  private def makeTestSkipped(testClass: String, testName: String): scala.xml.Elem = {
    nSkips += 1
    <testcase classname={testClass} name={testName} time="0"><skipped/></testcase>
  }

  private def makeTestFailed(testClass: String, testName: String, msg: String,
                             content: String, time: Int = 0): scala.xml.Elem = {
    nFailures += 1
    <testcase classname={testClass} name={testName} time={s"$time"}><failure message={s"FAILED: ${msg}"}>{content}</failure></testcase>
  }

  private def testReportValues(jobId: Int, reportId: UUID,
                               rptTest: ReportTestRules): Int = {
    Try {
      // FIXME this actually works for any job type
      Await.result(sal.getAnalysisJobReport(jobId, reportId), TIMEOUT)
    } match {
      case Success(report) => {
        println(s"Report ${report.id}:")
        val testClass = "Test" + (for (word <- report.id.split('.').last.split("_")) yield {word.capitalize}).toList.mkString("")
        // always check that datastore and report have the same UUID
        var uuidTest = if (report.uuid == reportId) {
          println(s"  passed: report.uuid == '${reportId}'")
          makeTestPassed(testClass, "test_report_uuid")
        } else {
          println(s"  FAILED: report.uuid != '${reportId}'")
          makeTestFailed(testClass, "test_report_uuid",
                         "report.uuid != reportId",
                         s"${reportId} != ${report.uuid}")
        }
        testCases.append(uuidTest)
        // iterate over rules defined in testkit cfg JSON
        (for (rule <- rptTest.rules) yield {
          val isSameId = (other: String) => ((other != "") && (other.split('.').toList.last == rule.attrId))
          var testStatus: TestResult = TestSkipped
          // I'm writing this as an N^2 loop because it's much cleaner and
          // N will usually be single digits
          for (a <- report.attributes) {
            var testStatusAttr: TestResult = a match {
              case ReportDoubleAttribute(id,name,value) => if (isSameId(id)) {
                if (testReportValue[Double](value, rule.value.asInstanceOf[Double], rule.op)) TestPassed else TestFailed
              } else TestSkipped
              case ReportLongAttribute(id,name, value) => if (isSameId(id)) {
                if (testReportValue[Long](value, rule.value.asInstanceOf[Long], rule.op)) TestPassed else TestFailed
              } else TestSkipped
              case ReportBooleanAttribute(id,name, value) => if (isSameId(id)) {
                val expected = rule.value.asInstanceOf[Boolean]
                if (rule.op == "eq") {
                  if (value == expected) TestPassed else TestFailed
                } else if (rule.op == "ne") {
                  if (value == expected) TestFailed else TestPassed
                } else throw new Exception(s"Operator ${rule.op} not supported for booleans")
              } else TestSkipped
              case ReportStrAttribute(id,name,value) => TestSkipped
            }
            testStatus = testStatusAttr match {
              case TestSkipped => testStatus
              case x => x
            }
          }
          val testStr = s"${rule.attrId} .${rule.op}. ${rule.value}"
          val testName = s"test_${rule.attrId}"
          var testCase = testStatus match {
            case TestPassed => makeTestPassed(testClass, testName)
            case TestFailed => makeTestFailed(testClass, testName, rule.attrId,
                                              testStr)
            case TestSkipped => makeTestSkipped(testClass, testName)
          }
          testCases.append(testCase)
          println(s"  ${testStatus.mode}: ${testStr}")
          testStatus match {
            case TestFailed => 1
            case _ => 0
          }
        }).toList.max
      }
      case Failure(err) => errorExit(err.getMessage)
    }
  }

  def runTests(reportTests: Seq[ReportTestRules], jobId: Int): Int = {
    val reports = Try {
      Await.result(sal.getAnalysisJobReports(jobId), TIMEOUT)
    } match {
      case Success(r) => r
      case Failure(err) => Seq[DataStoreReportFile]()
    }
    val reportsMap = (for (r <- reports) yield (r.reportTypeId, r.dataStoreFile.uuid)).toMap
    (for (test <- reportTests) yield {
      val reportId = reportsMap(test.reportId)
      testReportValues(jobId, reportId, test)
     }).toList.max
  }

  def writeTestResults(xunitOut: String): Unit = {
    val testSuite = <testsuite tests={nTests.toString} failures={nFailures.toString} skips={nSkips.toString} errors={nErrors.toString}>{for (t <- testCases) yield t}</testsuite>
    val pw = new PrintWriter(new File(xunitOut))
    try pw.write(testSuite.toString) finally pw.close()
  }

  protected def loadTestkitCfg(cfg: File): TestkitConfig = {
    val jsonSrc = Source.fromFile(cfg).getLines.mkString
    val jsonAst = jsonSrc.parseJson
    jsonAst.convertTo[TestkitConfig]
  }

  protected def runAnalysisTestJob(cfg: TestkitConfig): EngineJob = {
    println("Importing entry points...")
    val entryPoints = new ArrayBuffer[BoundServiceEntryPoint]
    for (ept <- cfg.entryPoints) {
       entryPoints.append(importEntryPoint(ept.entryId, ept.path))
    }
    val pipelineId = cfg.pipelineId match {
      case Some(id) => id
      case _ => cfg.workflowXml match {
        case Some(xmlFile) => getPipelineId(Paths.get(xmlFile))
        case _ => throw new Exception("Either a pipeline ID or workflow XML must be defined")
      }
    }
    val presets = cfg.presetXml match { // this is clumsy...
      case Some(presetXml) => getPipelinePresets(Some(Paths.get(presetXml)))
      case _ => getPipelinePresets(None)
    }
    val pipelineOptions = getPipelineServiceOptions(cfg.testId, pipelineId,
                                                    entryPoints, presets).get
    var jobId = 0
    Await.result(sal.runAnalysisPipeline(pipelineOptions), TIMEOUT)
  }

  protected def runImportDataSetTestJob(cfg: TestkitConfig): EngineJob = {
    if (cfg.entryPoints.size != 1) throw new Exception("A single dataset entry point is required for this job type.")
    val dsPath = cfg.entryPoints(0).path
    var dsMiniMeta = getDataSetMiniMeta(dsPath)
    // XXX should this automatically recover an existing job if present, or
    // always import again?
    Try { Await.result(sal.getDataSet(dsMiniMeta.uuid), TIMEOUT) } match {
      case Success(dsInfo) =>
        Await.result(sal.getJob(dsInfo.jobId), TIMEOUT)
      case Failure(err) =>
        Await.result(sal.importDataSet(dsPath, dsMiniMeta.metatype), TIMEOUT)
    }
  }

  protected def runMergeDataSetsTestJob(cfg: TestkitConfig): EngineJob = {
    if (cfg.entryPoints.size < 2) throw new Exception("At least two dataset entry points are required for this job type.")
    val entryPoints = cfg.entryPoints.map(e => importEntryPoint(e.entryId, e.path))

    val dsTypes = entryPoints.map(e => e.fileTypeId).toSet
    val dsType = if (dsTypes.size == 1) dsTypes.toList(0) else {
      throw new Exception(s"Multiple dataset types found: ${dsTypes.toList.mkString}")
    }
    // FIXME
    val dsMetaType = DataSetMetaTypes.fromString(dsType).get

    val fx = for {
      ids <- Future.sequence(entryPoints.map(e => sal.getDataSet(e.datasetId))).map(_.map(_.id))
      job <- sal.mergeDataSets(dsMetaType, ids, cfg.testId)
    } yield job

    Await.result(fx, TIMEOUT)
  }

  def runTestkitCfg(cfgFile: File, xunitOut: File, skipTests: Boolean = false,
                    ignoreTestFailures: Boolean = false): Int = {
    val cfg = loadTestkitCfg(cfgFile)
    println(cfg.testId)
    var jobId = -1
    var xc = Try {
      cfg.jobType match {
        case "pbsmrtpipe" => runAnalysisTestJob(cfg)
        case "import-dataset" => runImportDataSetTestJob(cfg)
        case "merge-datasets" => runMergeDataSetsTestJob(cfg)
        case _ => throw new Exception(s"Don't know how to run job type ${cfg.jobType}")
      }
    } match {
      case Success(jobInfo) => {
        printJobInfo(jobInfo)
        jobId = jobInfo.id
        if (! jobInfo.isComplete) {
          println(s"Job ${jobInfo.uuid} started")
          waitForJob(jobInfo.uuid)
        } else if (jobInfo.isSuccessful) 0 else 1
      }
      case Failure(err) => errorExit(err.getMessage)
    }
    if ((xc == 0) && (! skipTests)) {
      var testStatus = if (cfg.reportTests.size != 0) runTests(cfg.reportTests, jobId) else 0
      writeTestResults(xunitOut.getAbsolutePath)
      if (ignoreTestFailures) 0 else testStatus
    } else xc
  }

  def runTestsOnly(cfgFile: File, testJobId: Int, xunitOut: File,
                   ignoreTestFailures: Boolean = false): Int = {
    if (runGetJobInfo(testJobId) != 0) return errorExit(s"Couldn't retrieve job ${testJobId}")
    val cfg = loadTestkitCfg(cfgFile)
    if (cfg.reportTests.size > 0) {
      var testStatus = runTests(cfg.reportTests, testJobId)
      writeTestResults(xunitOut.getAbsolutePath)
      if (ignoreTestFailures) 0 else testStatus
    } else errorExit("No tests defined")
  }
}

object TestkitRunner {
  def apply (c: TestkitParser.TestkitConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    val sal = new SmrtLinkServiceAccessLayer(c.host, c.port)(actorSystem)
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
    val xc = TestkitParser.parser.parse(args, TestkitParser.defaults) match {
      case Some(config) => TestkitRunner(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args.to[scala.collection.immutable.Seq])
}
