package com.pacbio.secondary.lims.services

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.Database
import spray.routing.HttpService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Try}
import spray.json._
import DefaultJsonProtocol._
import com.pacbio.secondary.lims.JsonProtocol._


trait ResolveDataSet extends HttpService {
  this: Database =>

  /**
   * Routes related to resolving LimsSubreadSet as per specification.md
   *
   * GET /smrt-lims/lims-subreadset/{Subreadset-UUID} # Returns LimsSubreadSet Resource
   * GET /smrt-lims/lims-subreadset/{RUN-CODE}        # Returns LimsSubreadSet Resource
   * GET /smrt-lims/lims-subreadset/{experiment-id}   # Return List of LimsSubreadSet Resource or empty List if exp id isn't found
   */
  val resolveDataSetRoutes =
    path("subreadset" / Segment) {
      q => {
        get {
          // serialize as JSON with correct HTTP status code
          resolveLimsSubreadSet (q) match {
            case lys: Seq[LimsYml] =>
              complete(if (lys.nonEmpty) 200 else 404, lys.toJson.prettyPrint)
            case t: Throwable => throw t // TODO: better error message for HTTP 500?
          }
        }
      }
    }

  // TODO: move this to an actor, probably too slow to keep on request dispatcher
  def resolveLimsSubreadSet(q: String): Seq[LimsYml] = {
    // attempt looking up the various ids
    Try(getByExperiment(q.toInt)) match {
      case Success(id :: ids) => getLimsYml(id :: ids)
      case _ =>
        Try(getByRunCode(q)) match {
          case Success(id :: ids) => getLimsYml(id :: ids)
          case _ =>
            Try(getByAlias(q)) match {
              case Success(id) => Seq(getLimsYml(id))
              case _ => Seq[LimsYml]()
            }
        }
    }
  }

  val resolveRoutes = resolveDataSetRoutes
}