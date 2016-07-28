package com.pacbio.secondary.lims.services

import java.util.UUID

import com.pacbio.secondary.lims.database.Database
import spray.routing.HttpService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import spray.json._
import DefaultJsonProtocol._
import com.pacbio.secondary.lims.LimsJsonProtocol._
import com.pacbio.common.services.utils.CORSSupport.cors


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
    // match by runcode
    path("smrt-lims" / "lims-subreadset" / "runcode" / "^([0-9]{7}-[0-9]{4})$".r) { rc =>
      get { ctx =>
        Future {
          val v = subreadsByRunCode(rc)
          ctx.complete(if (v.nonEmpty) 200 else 404, v.toJson.prettyPrint)
        }
      }
    } ~
    // match by experiment ID
    path("smrt-lims" / "lims-subreadset" / "expid" / IntNumber) { expid =>
      get { ctx =>
        Future {
          val v = subreadsByExperiment(expid)
          ctx.complete(if (v.nonEmpty) 200 else 404, v.toJson.prettyPrint)
        }
      }
    } ~
    // UUID lookup
    path("smrt-lims" / "lims-subreadset" / "uuid" / JavaUUID) { uuid =>
      get(ctx => Future(ctx.complete(subread(uuid).toJson.prettyPrint)))
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
    path("smrt-lims" / "resolver" / Segment / Segment ) { // TODO: can specify dataset-type-short-name as regex
      (dt, q) => {
        // GET = resolve the alias to the dataset type
        get {
          complete(subreadByAlias(q).toJson.prettyPrint) // TODO: match `dt` and do dataset-type-short-name specific alias
        } ~
        // PUT = set the alias for the dataset type
        post {
          parameters('name) { name =>
            Try(setAlias(name, UUID.fromString(q), "lims-subreadset")) match {
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