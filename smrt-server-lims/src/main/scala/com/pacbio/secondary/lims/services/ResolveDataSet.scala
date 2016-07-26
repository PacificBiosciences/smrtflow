package com.pacbio.secondary.lims.services

import com.pacbio.secondary.lims.database.Database
import spray.routing.HttpService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import spray.json._
import DefaultJsonProtocol._
import com.pacbio.secondary.lims.JsonProtocol._
import com.pacbio.common.services.utils.CORSSupport.cors
import com.pacbio.secondary.lims.LimsSubreadSet


trait ResolveDataSet extends HttpService {
  this: Database =>

  /**
   * Routes related to resolving LimsSubreadSet as per specification.md
   *
   * GET /smrt-lims/lims-subreadset/{Subreadset-UUID} # Returns LimsSubreadSet Resource
   * GET /smrt-lims/lims-subreadset/{RUN-CODE}        # Returns LimsSubreadSet Resource
   * GET /smrt-lims/lims-subreadset/{experiment-id}   # Return List of LimsSubreadSet Resource or empty List if exp id isn't found
   */
  val resolveLimsSubreadSetRoutes = cors {
    path("subreadset" / Segment) {
      q => {
        get {
          ctx =>
            Future {
              // serialize as JSON with correct HTTP status code
              resolveLimsSubreadSet(q) match {
                case lys: Seq[LimsSubreadSet] =>
                  ctx.complete(if (lys.nonEmpty) 200 else 404, lys.toJson.prettyPrint)
              }
            }
        }
      }
    }
  }

  // TODO: move this to an actor, probably too slow to keep on request dispatcher
  def resolveLimsSubreadSet(q: String): Seq[LimsSubreadSet] = {
    // attempt looking up the various ids
    Try(subreadsByExperiment(q.toInt)) match {
      case Success(ly :: lys) => ly :: lys
      case _ =>
        Try(subreadsByRunCode(q)) match {
          case Success(ly :: lys) => ly :: lys
          case _ =>
            Try(subreadByAlias(q)) match {
              case Success(ly) => Seq[LimsSubreadSet](ly)
              case _ => Seq[LimsSubreadSet]()
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
    path("resolver" / Segment / Segment ) { // TODO: can specify dataset-type-short-name as regex
      (dt, q) => {
        // GET = resolve the alias to the dataset type
        get {
          complete(subreadByAlias(q).toJson.prettyPrint) // TODO: match `dt` and do dataset-type-short-name specific alias
        } ~
        // PUT = set the alias for the dataset type
        post {
          parameters('name) { name =>
            Try(setAlias(name, q, "lims_subreadset")) match {
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