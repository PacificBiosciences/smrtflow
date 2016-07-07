package com.pacbio.secondary.lims

import com.pacbio.secondary.lims.database.Database
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSetUUID}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterEach
import spray.http._
import spray.testkit.Specs2RouteTest


/**
 * Tests using POST to import data and GET to resolve the respective UUID
 *
 * This is a simple integration test that ensures several things.
 *
 * - Routes are wired correctly and work
 * - lims.yml import works
 * - Full UUID and short UUID resolution works
 */
class RouteImportAndResloveSpec extends Specification with Specs2RouteTest with ImportLimsYml with ResolveDataSetUUID {
  def actorRefFactory = system

  // ensure just this test uses the test DB
  Database.synchronized {
    // toggle on fresh in-memory DB
    Database.nextTest()

    "lims.yml import should be resolvable" in {
      // in-mem version of `cat /net/pbi/collections/322/3220001/r54003_20160212_165105/1_A01/lims.yml`
      val uuid = "1695780a2e7a0bb7cb1e186a3ee01deb"
      val content =
        s"""expcode: 3220001
         |runcode: '3220001-0006'
         |path: 'file:///pbi/collections/322/3220001/r54003_20160212_165105/1_A01'
         |user: 'MilhouseUser'
         |uid: '$uuid'
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
    val
      httpEntity = HttpEntity(MediaTypes.`multipart/form-data`, HttpData(content)).asInstanceOf[HttpEntity.NonEmpty]
    val
    formFile = FormFile("file", httpEntity)
    val
    mfd = MultipartFormData(Seq(BodyPart(formFile, "file")))
      Post("/import", mfd) ~> sealRoute(importLimsYmlRoutes) ~> check {

        // confirm that the POST worked without error
        response.status.isSuccess mustEqual true

        // match full UUID
        val db = Database.get()
        uuid mustEqual db.getByUUID(uuid)

        // match partial UUID
        uuid mustEqual db.getByUUIDPrefix(uuid.substring(0, 6))

        // resolve complete UUID
        Get(s"/resolve?q=$uuid") ~> sealRoute(resolveRoutes) ~> check {
          response.status.isSuccess mustEqual true
          uuid mustEqual response.entity.data.asString
      }

        // resolve partial UUID
        Get(s"/resolve?q=${uuid.substring(0, 6)}") ~> sealRoute(resolveRoutes) ~> check {
          response.status.isSuccess mustEqual true
          uuid mustEqual response.entity.data.asString
      }
    }
  }
  }
}