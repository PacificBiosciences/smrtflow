package com.pacbio.secondary.lims

import com.pacbio.secondary.lims.database.TestDatabase
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSetUUID}
import org.specs2.mutable.Specification
import spray.http._
import spray.testkit.Specs2RouteTest

import com.pacbio.secondary.lims.JsonProtocol._
import spray.json.DefaultJsonProtocol._
import spray.json._


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
  with ImportLimsYml
  with ResolveDataSetUUID {

  def actorRefFactory = system

  // force these tests to run sequentially since they can lock up the database
  sequential

  val expcode = 3220001
  val runcode = "3220001-0006"
  val alias = "Foo"

  lazyCreateTables

  "Internal LIMS service" should {
    "Pre-import expcode is not resolvable via GET" in {
      Get(s"/resolve?q=$expcode") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual false
      }
    }
    "Import data from POST" in {
      // in-mem version of `cat /net/pbi/collections/322/3220001/r54003_20160212_165105/1_A01/lims.yml`
      val content =
        s"""expcode: $expcode
            |runcode: '$runcode'
            |path: 'file:///pbi/collections/322/3220001/r54003_20160212_165105/1_A01'
            |user: 'MilhouseUser'
            |uid: '1695780a2e7a0bb7cb1e186a3ee01deb'
            |tracefile: 'm54003_160212_165114.trc.h5'
            |description: 'TestSample'
            |wellname: 'A01'
            |cellbarcode: '00000133635908926745416610'
            |seqkitbarcode: '002222100620000123119'
            |cellindex: 0
            |colnum: 0
            |samplename: 'TestSample'
            |instid: 90""".stripMargin

      // post the data from the file
      val httpEntity = HttpEntity(MediaTypes.`multipart/form-data`, HttpData(content)).asInstanceOf[HttpEntity.NonEmpty]
      val formFile = FormFile("file", httpEntity)
      val mfd = MultipartFormData(Seq(BodyPart(formFile, "file")))

      this.loadData(content.getBytes)

      Post("/import", mfd) ~> sealRoute(importLimsYmlRoutes) ~> check {
        response.status.isSuccess mustEqual true
      }
    }
    "Full expcode resolvable via API" in {
      expcode mustEqual getLimsYml(getByExperiment(expcode).head).expcode
    }
    "Full expcode resolvable via GET" in {
      Get(s"/resolve?q=$expcode") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual true
        expcode mustEqual response.entity.data.asString.parseJson.convertTo[Seq[LimsYml]].head.expcode
      }
    }
    "Full runcode resolvable via API" in {
      runcode mustEqual getLimsYml(getByRunCode(runcode).head).runcode
    }
    "Full runcode resolvable via GET" in {
      Get(s"/resolve?q=$runcode") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual true
        runcode mustEqual response.entity.data.asString.parseJson.convertTo[Seq[LimsYml]].head.runcode
      }
    }
    "Alias resolvable via API" in {
      val id = getByExperiment(expcode).head
      setAlias(alias, id)
      id mustEqual getByAlias(alias)
    }
    "Alias resolvable via GET" in {
      Get(s"/resolve?q=$alias") ~> sealRoute(resolveRoutes) ~> check {
        response.status.isSuccess mustEqual true
        runcode mustEqual response.entity.data.asString.parseJson.convertTo[Seq[LimsYml]].head.runcode
      }
    }
  }
}