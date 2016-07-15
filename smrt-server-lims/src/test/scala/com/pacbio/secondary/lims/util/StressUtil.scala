package com.pacbio.secondary.lims.util

import java.lang.System.nanoTime
import java.util.concurrent.Executors

import com.pacbio.secondary.lims.JsonProtocol._
import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.services.{ImportLimsYml, ResolveDataSet}
import org.specs2.mutable.Specification
import spray.http.{BodyPart, _}
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.testkit.Specs2RouteTest

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.duration.Duration


/**
 * Shared methods for creating and loading mock data
 *
 * This functionality is best exercised and demonstrated in the stress testing; however, it is
 * generally helpful for any case where mock data is needed for testing.
 *
 * See README.md#Tests testing for examples.
 */
trait StressUtil {
  this: Specification with Specs2RouteTest with ImportLimsYml with ResolveDataSet =>

  def stressTest(c: StressConfig, ec : ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))) : StressResults = {
    // wait for all the imports to finish
    val postImportsF = for (i <- 1 to c.imports) yield Future(postLimsYml(mockLimsYml(i, s"$i-0001")))(ec)
    val postImports: Seq[Boolean] = for (f <- postImportsF) yield Await.result(f, Duration(60, "seconds"))
    // wait for all the queries to finish
    val getExpF = for (i <- 1 to c.imports) yield (for (j <- 1 to c.queryReps) yield Future(getExperimentOrRunCode(i))(ec))
    val getRuncodeF = for (i <- 1 to c.imports) yield (for (j <- 1 to c.queryReps) yield Future(getExperimentOrRunCode(s"$i-0001"))(ec))
    val getExp: Seq[(Boolean, Seq[LimsYml])] = for (f <- getExpF.flatten) yield Await.result(f, Duration(60, "seconds"))
    val getRuncode: Seq[(Boolean, Seq[LimsYml])] = for (f <- getRuncodeF.flatten) yield Await.result(f, Duration(60, "seconds"))
    // return the results
    new StressResults(postImports, getExp, getRuncode)
  }

  /**
   * GET request to lookup existing data by Experiment or Run Code
   */
  def getExperimentOrRunCode(expOrRunCode: String) : (Boolean, Seq[LimsYml]) = {
    implicit val defaultTimeout = RouteTestTimeout(Duration(30, "seconds"))

    Get(s"/subreadset/$expOrRunCode") ~> sealRoute(resolveRoutes) ~> check {
      (response.status.isSuccess, response.entity.data.asString.parseJson.convertTo[Seq[LimsYml]])
    }
  }
  def getExperimentOrRunCode(v: Int) : (Boolean, Seq[LimsYml]) = getExperimentOrRunCode(v.toString)

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
  def mockLimsYml(
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

case class StressConfig (imports: Int, queryReps: Int)

class StressResults(
    val postImports: Seq[Boolean],
    val getExp: Seq[(Boolean, Seq[LimsYml])],
    val getRuncode: Seq[(Boolean, Seq[LimsYml])]) {

  def noImportFailures(): Boolean = !postImports.exists(_ == false)

  def noLookupFailures(): Boolean =
    !List(getExp, getRuncode).flatten.map(v => v._1).exists(_ == false)
}