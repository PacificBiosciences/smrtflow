package com.pacbio.secondary.lims.util

import java.util.UUID
import java.util.concurrent.Executors

import com.pacbio.secondary.lims.LimsJsonProtocol._
import com.pacbio.secondary.lims.LimsSubreadSet
import com.pacbio.secondary.lims.services.{ImportLims, ResolveDataSet}
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
trait StressUtil extends MockUtil {
  this: Specification with Specs2RouteTest with ImportLims with ResolveDataSet =>

  def stressTest(c: StressConfig, ec : ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))) : StressResults = {
    // wait for all the imports to finish
    val postImportsF = for (i <- 1 to c.imports) yield Future(postLimsYml(mockLimsYml(i, mockRuncode(i))))(ec)
    val postImports: Seq[Boolean] = for (f <- postImportsF) yield Await.result(f, Duration(60, "seconds"))
    // wait for all the queries to finish
    val getExpF = for (i <- 1 to c.imports) yield (for (j <- 1 to c.queryReps) yield Future(getExperiment(i))(ec))
    val getRuncodeF = for (i <- 1 to c.imports) yield (for (j <- 1 to c.queryReps) yield Future(getRunCode(mockRuncode(i)))(ec))
    val getExp: Seq[(Boolean, Seq[LimsSubreadSet])] = for (f <- getExpF.flatten) yield Await.result(f, Duration(60, "seconds"))
    val getRuncode: Seq[(Boolean, Seq[LimsSubreadSet])] = for (f <- getRuncodeF.flatten) yield Await.result(f, Duration(60, "seconds"))
    // return the results
    new StressResults(postImports, getExp, getRuncode)
  }

  /**
   * GET request to lookup existing data by Experiment or Run Code
   */
  def getExperiment(expid: Int) : (Boolean, Seq[LimsSubreadSet]) = {
    implicit val defaultTimeout = RouteTestTimeout(Duration(30, "seconds"))

    Get(s"/smrt-lims/lims-subreadset/expid/$expid") ~> sealRoute(resolveRoutes) ~> check {
      (response.status.isSuccess, response.entity.data.asString.parseJson.convertTo[Seq[LimsSubreadSet]])
    }
  }

  def getRunCode(runcode: String) : (Boolean, Seq[LimsSubreadSet]) = {
    implicit val defaultTimeout = RouteTestTimeout(Duration(30, "seconds"))

    Get(s"/smrt-lims/lims-subreadset/runcode/$runcode") ~> sealRoute(resolveRoutes) ~> check {
      (response.status.isSuccess, response.entity.data.asString.parseJson.convertTo[Seq[LimsSubreadSet]])
    }
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
    Post("/smrt-lims/lims-subreadset/import", mfd) ~> sealRoute(importLimsRoutes) ~> check {
      response.status.isSuccess
    }
  }
}

case class StressConfig (imports: Int, queryReps: Int)

class StressResults(
    val postImports: Seq[Boolean],
    val getExp: Seq[(Boolean, Seq[LimsSubreadSet])],
    val getRuncode: Seq[(Boolean, Seq[LimsSubreadSet])]) {

  def noImportFailures(): Boolean = !postImports.exists(_ == false)

  def noLookupFailures(): Boolean =
    !List(getExp, getRuncode).flatten.map(v => v._1).exists(_ == false)
}