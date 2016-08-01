package com.pacbio.secondary.lims

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.{Files, Paths}
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.io.IO
import com.pacbio.secondary.lims.util.MockUtil
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}
import spray.can.Http
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.{Duration, FiniteDuration}
import com.pacbio.secondary.lims.tools.LimsClientToolsApp


trait CommandLineToolsConfig {
  val testHost = "127.0.0.1"
  val testPort = 8081
}

class TestInternalServiceActor
  extends InternalServiceActor
  with CommandLineToolsConfig {

  override lazy val conf = ConfigFactory.parseString(
    s"""pb-services {
        |  db-uri = "jdbc:h2:mem:CLI_TEST;DB_CLOSE_DELAY=3"
        |  host = "$testHost"
        |  port = $testPort
        |}""".stripMargin)

  // need to make the tables before test run or else slower CI such as CircleCI timeout
  createTables()
}

/**
 * Runs the CLI and API equivalent against mock data to assert the code paths work as expected
 */
class CommandLineToolsSpec
  extends Specification
  with Specs2RouteTest
  // helper tools to mock up data
  with MockUtil
  with CommandLineToolsConfig {

  // TODO: can remove this when specs2 API is upgraded
  override def map(fragments: =>Fragments) = Step(beforeAll) ^ fragments ^ Step(afterAll)

  def beforeAll = {
    val service = system.actorOf(Props[TestInternalServiceActor], "smrt-lims")
    IO(Http) ! Http.Bind(service, testHost, testPort)
  }
  def afterAll = IO(Http) ! Http.Unbind(Duration(10, "seconds"))

  def actorRefFactory = system

  private implicit val timeout = RouteTestTimeout(new FiniteDuration(10, TimeUnit.SECONDS))

  // force these tests to run sequentially since later tests rely on logic in earlier tests
  sequential

  val uuid = UUID.fromString("5fe01e82-c694-4575-9173-c23c458dd0e1")
  val expid = 3220001
  val runcode = "3220001-0006"
  val tracefile = "m54003_160212_165114.trc.h5"
  // temp directory used for import
  val dir = Files.createTempDirectory(Paths.get("/tmp"), "CLI")

  // util method to buffer CLI stderr and provide a string for testing
  private def stderr(f: => Unit) : String = {
    val err = System.err
    try {
      val buf = new ByteArrayOutputStream()
      System.setErr(new PrintStream(buf))
      f
      buf.toString()
    }
    finally {
      System.setErr(err)
    }
  }

  // util method to buffer CLI stdout and provide a string for testing
  private def stdout(f: => Unit) : String = {
    val out = System.out
    try {
      val buf = new ByteArrayOutputStream()
      System.setOut(new PrintStream(buf))
      f
      buf.toString()
    }
    finally {
      System.setOut(out)
    }
  }

  val commonArgs = List[String](
    "--host", testHost,
    "--port", testPort.toString,
    "--no-jvm-exit")

  def runCli(args: List[String]) = LimsClientToolsApp.main(args.toArray)

  def cliLookupViaExp: Unit = runCli("get-expid" :: "--expid" :: expid.toString :: commonArgs)

  def cliLookupViaRuncode: Unit = runCli("get-runcode" :: "--runcode" :: runcode :: commonArgs)

  def cliLookupViaUuid: Unit = runCli("get-uuid" :: "--uuid" :: uuid.toString :: commonArgs)

  // string serialization expected to be observed in all CLI and API output
  val serializedLimsSubreadSet = s"LimsSubreadSet(5fe01e82-c694-4575-9173-c23c458dd0e1,3220001,3220001-0006,${dir.toString},3.0.17,3.0.5.175014,A01,m54009_160426_165001,2016-04-26T18:46:06Z,Inst54009,54009)"
  val cliLookupFail =
    s"""|Failed to run spray.httpx.UnsuccessfulResponseException: Status: 404 Not Found
        |Body: []
        |""".stripMargin

  "Internal LimsSubreadDataSet services" should {
    "API should fail pre-import: lookup via expcode" in (1 mustEqual LimsClientToolsApp.runGetSubreadsByExp(testHost, testPort, expid))
    // TODO: 404 doesn't mean the code failed -- unless we decide on that semantic
    "CLI should fail pre-import: get-expcode" in (stderr(cliLookupViaExp) mustEqual cliLookupFail)
    "API should fail pre-import: lookup via runcode" in (1 mustEqual LimsClientToolsApp.runGetSubreadsByRuncode(testHost, testPort, runcode))
    "CLI should fail pre-import: get-runcode" in (stderr(cliLookupViaRuncode) mustEqual cliLookupFail)
    "Import data from POST" in {
      val limsYml = dir.resolve("lims.yml")
      val subreadset = dir.resolve(tracefile.replace(".trc.h5", ".subreadset.xml"))
      try {
        // write the file. change path + context so the auto-lookup of .subreadset.xml matches the mock data
        Files.write(limsYml, mockLimsYml(expid, runcode, path = dir.toString, tracefile = tracefile).getBytes)
        Files.write(subreadset, mockSubreadset().getBytes)
        stdout(runCli("import" :: "--path" :: limsYml.toString :: commonArgs)) mustEqual s"Merged: $serializedLimsSubreadSet\n"
      } finally List(limsYml, subreadset, dir).foreach(Files.delete)
    }
    "API lookup via expcode works" in (0 mustEqual LimsClientToolsApp.runGetSubreadsByExp(testHost, testPort, expid))
    "CLI get-expcode works" in (stdout(cliLookupViaExp) mustEqual s"List($serializedLimsSubreadSet)\n")
    "API lookup via runcode works" in (0 mustEqual LimsClientToolsApp.runGetSubreadsByRuncode(testHost, testPort, runcode))
    "CLI get-expcode works" in (stdout(cliLookupViaRuncode) mustEqual s"List($serializedLimsSubreadSet)\n")
    "API lookup via UUID works" in (0 mustEqual LimsClientToolsApp.runGetSubreadByUUID(testHost, testPort, uuid))
    "CLI get-uuid works" in (stdout(cliLookupViaUuid) mustEqual s"Some($serializedLimsSubreadSet)\n")
  }
}