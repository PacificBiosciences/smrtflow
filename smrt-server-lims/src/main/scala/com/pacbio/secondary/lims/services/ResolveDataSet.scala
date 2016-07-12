package com.pacbio.secondary.lims.services

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.Database
import spray.routing.HttpService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
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
  val resolveLimsSubreadSetRoutes =
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

  /**
   * Routes related to resolving LimsSubreadSet as per specification.md
   *
   * GET /smrt-lims/resolver/{dataset-type-short-name}/{name-id}
   * POST /smrt-lims/resolver/{dataset-type-short-name}/{UUID} name="name-id"
   * DELETE /smrt-lims/resolver/{datasetyp-type-short-name}/{name-id} # "Unregister `name-id` to specific SubreadSet"
   */
  val resolveDataSetRoutes =
    path("resolver" / Segment / Segment) { // TODO: can specify dataset-type-short-name as regex
      (dt, id) => {
        // GET = resolve the alias to the dataset type
        get {
          complete(getLimsYml(getByAlias(id)).toJson.prettyPrint) // TODO: match `dt` and do dataset-type-short-name specific alias
        } ~
        // PUT = set the alias for the dataset type
        post {
          parameters('name) { name =>
            Try(setAlias(name, id.toInt)) match {
              case Success(_) => complete("Set $dt as alias for $id") // TODO: match `dt` and do dataset-type-short-name specific alias
              case Failure(t) => complete(500, "Couldn't set alias '$id' for $dt")
            }
          }
        } ~
        // DELETE = remove the alias
        delete {
          parameters('name) { name =>
            Try(delAlias(name)) match {
              case Success(_) => complete("Deleted $id as $dt alias") // TODO: match `dt` and do dataset-type-short-name specific alias
              case Failure(t) => complete(500, "Error. '$id' not deleted for $dt")
            }
          }
        }
      }
    }

  val resolveRoutes = resolveLimsSubreadSetRoutes ~ resolveDataSetRoutes
}