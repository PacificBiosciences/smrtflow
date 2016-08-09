package com.pacbio.secondary.lims

import java.io.{ByteArrayInputStream, File}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.io.IO
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.lims.LimsJsonProtocol._
import com.pacbio.secondary.lims.database.TestDatabase
import com.pacbio.secondary.lims.services.{ImportLims, ResolveDataSet}
import com.pacbio.secondary.lims.tools.LimsClientToolsApp
import com.pacbio.secondary.lims.util._
import com.pacificbiosciences.pacbiodatasets.SubreadSet
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}
import spray.can.Http
import spray.http._
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}


/**
 * Covers a gap in the test logic to ensure multiple uploads and returned results work
 *
 * Specifically, these are the two cases covered here. This test is split from other tests so
 * that they are not complicated. It is also assumed that if 2 work then N works -- it is unclear
 * what number of imports and results per response are a reasonable max. It is clear that showing
 * more-than-1-support is helpful and otherwise not well tested.
 *
 * 1. lims.yml imports have two code paths. This tests path (b) below.
 *   a. Import the explicit path of a lims.yml file
 *   b. Recursively scan a directory and import any lims.yml files found.
 *
 * 2. Queries to the RESTful API use the same code path regardless of if 1 or more results are
 *    returned. Actually returning more than one helps confirm that logic works for N > 1.
 *
 * 3. A `.subreadset.xml` file that 1-to-1 matches a lims.yml should match the movie context name,
 * but it need not as long as it is in the same directory. A convenient add on to this test is
 * confirming that.
 *    a. If only one .subreadset.xml exists and it doesn't match the movie context name, use it.
 *    b. If (somehow) more than one .subreadset.xml exists and one matches the movie context name,
 * use the one that matches.
 */
class MultiImportAndResultsSpec
  extends Specification
  with Specs2RouteTest
  with MockUtil
  with CommandLineUtil
  with CommandLineToolsConfig {

  // TODO: can remove this when specs2 API is upgraded
  override def map(fragments: =>Fragments) = Step(beforeAll) ^ fragments ^ Step(afterAll)

  def beforeAll = {
    val service = system.actorOf(Props[TestMultiInternalServiceActor], "smrt-lims")
    IO(Http) ! Http.Bind(service, testHost, testPort)
  }
  def afterAll = IO(Http) ! Http.Unbind(Duration(10, "seconds"))

  def actorRefFactory = system

  private implicit val timeout = RouteTestTimeout(new FiniteDuration(10, TimeUnit.SECONDS))

  // force these tests to run sequentially since later tests rely on logic in earlier tests
  sequential

  case class Exp(expid: Int, uuid: UUID, runcode: String, tracefile: String)

  // base directory. if recursive logic steps one dir, it should do N -- using std Java API
  val baseDir = Files.createTempDirectory(Paths.get("/tmp"), "mutli_import_and_results")
  val run1 = Exp(3220001, UUID.randomUUID(), "3220001-0001", "m54003_160212_000001.trc.h5")
  val run2 = Exp(3220002, UUID.randomUUID(), "3220002-0001", "m54003_160213_000001.trc.h5")
  val run3 = Exp(3220002, UUID.randomUUID(), "3220002-0002", "m54003_160213_000002.trc.h5")
  val runs = Set(run1, run2, run3)

  val commonArgs = List[String](
    "--host", testHost,
    "--port", testPort.toString,
    "--no-jvm-exit")

  def runCli(args: List[String]) = LimsClientToolsApp.main(args.toArray)

  /**
   * Makes up the temp directory and returns a list of files for later deletion
   * @return
   */
  def makeMockRuns(): Map[Exp, (Path, Path, Path)] = {
    val dirs = runs.map(e => baseDir.resolve(e.runcode))
    dirs.exists(!_.toFile.mkdir()) mustEqual false
    (for ((e, dir) <- runs zip dirs) yield {
      // two temp files in dir
      val limsYml = dir.resolve("lims.yml")
      val subreadset = dir.resolve(e.tracefile.replace(".trc.h5", ".subreadset.xml"))
      // write the file. change path + context so the auto-lookup of .subreadset.xml matches the mock data
      Files.write(limsYml, mockLimsYml(e.expid, e.runcode, path = dir.toString, tracefile = e.tracefile).getBytes)
      Files.write(subreadset, mockSubreadset(uuid = e.uuid).getBytes)
      (e, (limsYml, subreadset, dir))
    }).toMap
  }

  private def outputLine(r: Exp, dir: Path) : String =
    s"Merged: LimsSubreadSet(${r.uuid},${r.expid},${r.runcode},$dir,3.0.17,3.0.5.175014,A01,m54009_160426_165001,2016-04-26T18:46:06Z,Inst54009,54009)"

  "Internal LimsSubreadDataSet services" should {
    "Import data from POST" in {
      val files = makeMockRuns()
      val out = stdout(runCli("import" :: "--path" :: baseDir.toString :: commonArgs))
      //files.values.foreach(Files.delete)
      files.values.map { v =>
        Files.delete(v._1)
        Files.delete(v._2)
        Files.delete(v._3)
      }
      // order doesn't matter here. import is a race condition
      true mustEqual out.contains(outputLine(run1, files(run1)._3))
      true mustEqual out.contains(outputLine(run2, files(run2)._3))
      true mustEqual out.contains(outputLine(run3, files(run3)._3))
    }
    "CLI lookup finds all runs" in {
      0 mustEqual runs.map(r => LimsClientToolsApp.runGetSubreadsByExp(testHost, testPort, r.expid)).sum
    }
  }
}

// since tests run simultaneously, need to alter port
class TestMultiInternalServiceActor extends TestInternalServiceActor {
  override val testPort = 8091
}