package com.pacbio.secondary.lims

import com.pacbio.secondary.lims.database.TestDatabase
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSet}
import org.specs2.mutable.Specification
import spray.http.{BodyPart, HttpData, HttpEntity, _}
import spray.testkit.Specs2RouteTest

/**
 * Performs a stress test of the LIMS import and alias services
 *
 * WIP: will remove this comment when done.
 *
 * Designed to be helpful for the following use cases.
 *
 * - Spec that verifies importing multiple lims.yml and making multiple aliases
 * - Performance tuning of a disk-backed DB
 *   - avg/min/max time per data creation (aka are INSERTS and indexing fast enough?)
 *   - avg/min/max per query for all queries (aka are SELECTS and JOINS fast enough?)
 * - Comparing different database backends
 */
class StressTestSpec extends Specification
    // Probably should bind a server and make RESTful calls
    with Specs2RouteTest
    // swap the DB here. TestDatabase is in-memory
    with TestDatabase
    // routes that will use the test database
    with ImportLimsYml
    with ResolveDataSet {

  def actorRefFactory = system

  /**
   * Creates all the mock data and times queries
   */
//  def main(args: Array[String]): Unit = {
  "Multiple lims.yml files should import correctly" {
    val numLimsYml = 3
    val numReplicats = 3
    // insert all of the mock data in the database
    val successfulImports = for (i <- 1 to numLimsYml) yield {
      println("Running: " + i)
      postLimsYml(mockLimsYmlContent(i, s"$i-0001"))
    }

    println("Successful Imports: " + successfulImports.length)
  }


  /**
   * POST request with lims.yml content to make a database entry
   *
   * @param content
   * @return
   */
  def postLimsYml(content: String): Boolean = {
    val httpEntity = HttpEntity(MediaTypes.`multipart/form-data`, HttpData(content)).asInstanceOf[HttpEntity.NonEmpty]
    val formFile = FormFile("file", httpEntity)
    val mfd = MultipartFormData(Seq(BodyPart(formFile, "file")))
    loadData(content.getBytes)
    Post("/import", mfd) ~> sealRoute(importLimsYmlRoutes) ~> check {
      response.status.isSuccess
    }
  }

  /**
   * Creates a mock lims.yml file, allowing override of all values
   *
   * The history and semantics of all of these was unknown to @jfalkner. We'll have to fill them in
   * and enforce constraints in a later iteration.
   *
   * @param expcode
   * @param runcode
   * @param path
   * @param user
   * @param uuid
   * @param tracefile
   * @param desc
   * @param well
   * @param cellbarcode
   * @param seqkitbarcode
   * @param cellindex
   * @param colnum
   * @param samplename
   * @param instid
   * @return
   */
  def mockLimsYmlContent(
      // taken from `cat /net/pbi/collections/322/3220001/r54003_20160212_165105/1_A01/lims.yml`
      expcode: Int = 3220001,
      runcode: String = "3220001-0006",
      path: String = "file:///pbi/collections/322/3220001/r54003_20160212_165105/1_A01",
      user: String = "MilhouseUser",
      uuid: String = "1695780a2e7a0bb7cb1e186a3ee01deb",
      tracefile: String = "m54003_160212_165114.trc.h5",
      desc: String = "TestSample",
      well: String = "A01",
      cellbarcode: String = "00000133635908926745416610",
      seqkitbarcode: String = "002222100620000123119",
      cellindex: Int = 0,
      colnum: Int = 0,
      samplename: String = "TestSample",
      instid: Int = 90): String = {
    s"""expcode: $expcode
        |runcode: '$runcode'
        |path: '$path'
        |user: '$user'
        |uid: '$uuid'
        |tracefile: '$tracefile'
        |description: '$desc'
        |wellname: '$well'
        |cellbarcode: '$cellbarcode'
        |seqkitbarcode: '$seqkitbarcode'
        |cellindex: $cellindex
        |colnum: $colnum
        |samplename: '$samplename'
        |instid: $instid""".stripMargin
  }
}