package com.pacbio.secondary.lims.services

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.Database
import spray.routing.HttpService

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import spray.json._
import DefaultJsonProtocol._
import com.pacbio.secondary.lims.JsonProtocol._


object ResolveDataSetUUID {
  val uuidPrefixTree = null
  val jobToUUIDBucket = null
}

/**
 * Created by jfalkner on 6/30/16.
 */
trait ResolveDataSetUUID extends HttpService {
  this: Database =>

  val resolveRoutes =
    // lims.yml files must be posted to the server
    pathPrefix("resolve") {
      get {
        parameters('q) {
          q => {
            // serialize the tuples to JSON
            resolve(q).map(m => m) match {
              case lys: Seq[LimsYml] => complete(
                if (lys.nonEmpty) 200 else 400,
                lys.toJson.prettyPrint)
              case t: Throwable => throw t
            }
          }
        }
      }
    }

  /**
   * Resolves a dataset identifier to matching subreadsets
   */
  def resolve(q: String): Seq[LimsYml] = {
    // TODO: do these in parallel
    Try(getByExperiment(q.toInt)) match {
      case Success(ids) => getLimsYml(ids)
      case Failure(t) =>
        Try(getByRunCode(q)) match {
          case Success(ids) => getLimsYml(ids)
          case Failure(t) =>
            Try(getByAlias(q)) match {
            case Success(ids) => getLimsYml(ids)
            case Failure(t) => throw t
          }
        }
    }
  }
}