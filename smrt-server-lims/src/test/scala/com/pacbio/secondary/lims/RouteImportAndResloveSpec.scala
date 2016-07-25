package com.pacbio.secondary.lims

import java.util.concurrent.TimeUnit

import com.pacbio.secondary.lims.database.TestDatabase
import com.pacbio.secondary.lims.services.{ImportLims, ResolveDataSet}
import org.specs2.mutable.Specification
import spray.http._
import spray.testkit.Specs2RouteTest
import com.pacbio.secondary.lims.JsonProtocol._
import com.pacbio.secondary.lims.util.{StressUtil, TestLookupSubreadset}
import org.specs2.specification.{Fragments, Step}
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration.FiniteDuration


/**
 * Tests using POST to import data and GET to resolve the respective UUID
 *
 * This is a simple integration test that ensures several things.
 *
 * - Routes are wired correctly and work
 * - lims.yml import works
 * - Full UUID and short UUID resolution works
 */
class RouteImportAndResloveSpec
  extends Specification
  with Specs2RouteTest
  // test database config
  with TestDatabase
  // routes that will use the test database
  with ImportLims
  with TestLookupSubreadset
  with ResolveDataSet
  // helper tools to mock up data
  with StressUtil {

  // TODO: can remove this when specs2 API is upgraded
  override def map(fragments: =>Fragments) = Step(beforeAll) ^ fragments

  def beforeAll = createTables()

  def actorRefFactory = system

  private implicit val timeout = RouteTestTimeout(new FiniteDuration(10, TimeUnit.SECONDS))

  // force these tests to run sequentially since later tests rely on logic in earlier tests
  sequential

  val expcode = 3220001
  val runcode = "3220001-0006"
  val alias = "Foo"
  val alias2 = "Bar"

  "Internal LimsSubreadDataSet services" should {
    "Pre-import, expcode is not resolvable via GET" in {
      Get(s"/subreadset/$expcode") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual false
      }
    }
    "Import data from POST" in {
      // in-mem version of `cat /net/pbi/collections/322/3220001/r54003_20160212_165105/1_A01/lims.yml`
      val content = mockLimsYml(expcode, runcode)

      // post the data from the file
      val httpEntity = HttpEntity(MediaTypes.`multipart/form-data`, HttpData(content)).asInstanceOf[HttpEntity.NonEmpty]
      val formFile = FormFile("file", httpEntity)
      val mfd = MultipartFormData(Seq(BodyPart(formFile, "file")))
      loadData(content.getBytes)
      Post("/import", mfd) ~> sealRoute(importLimsRoutes) ~> check {
        response.status.isSuccess mustEqual true
      }
    }
    "Full expcode resolvable via API" in {
      expcode mustEqual subreadsByExperiment(expcode).head.expid
    }
    "Full expcode resolvable via GET /subreadset/<expcode>" in {
      Get(s"/subreadset/$expcode") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual true
        expcode mustEqual response.entity.data.asString.parseJson.convertTo[Seq[LimsSubreadSet]].head.expid
      }
    }
    "Full runcode resolvable via API" in {
      runcode mustEqual subreadsByRunCode(runcode).head.runcode
    }
    "Full runcode resolvable via GET" in {
      Get(s"/subreadset/$runcode") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual true
        runcode mustEqual response.entity.data.asString.parseJson.convertTo[Seq[LimsSubreadSet]].head.runcode
      }
    }
    "Alias resolvable via API" in {
      setAlias(alias, runcode) // TODO: this should be UUID but need an example for the test
      val ly = subreadByAlias(alias)
      (expcode, runcode) mustEqual (ly.expid, ly.runcode)
    }
    "Alias resolvable via GET /subreadset/<alias>" in {
      Get(s"/subreadset/$alias") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual true
        runcode mustEqual response.entity.data.asString.parseJson.convertTo[Seq[LimsSubreadSet]].head.runcode
      }
    }
    // tests the /resolve prefixed URIs. TODO: add in other dataset types
    "Alias resolvable via GET /resolver/<dataset-type>/<alias>" in {
      Get(s"/resolver/subreadset/$alias") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual true
        runcode mustEqual response.entity.data.asString.parseJson.convertTo[LimsSubreadSet].runcode
      }
    }
    "Alias creation via POST /resolver/<dataset-type>/<alias>" in {
      // assume this works based on previous test. TODO: better way to share this ID?
      Post(s"/resolver/subreadset/$runcode?name=$alias2") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual true
        runcode mustEqual subreadByAlias(alias2).runcode
      }
    }
    "Alias delete via DELETE /resolver/<dataset-type>/<alias>" in {
      Delete(s"/resolver/subreadset/$alias") ~> sealRoute(resolveRoutes) ~> check {
        response.status.intValue mustEqual 404
      }
    }
  }
}