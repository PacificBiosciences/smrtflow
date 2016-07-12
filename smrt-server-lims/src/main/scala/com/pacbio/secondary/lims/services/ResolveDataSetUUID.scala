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
    // LimsSubreadSet query and resolver for alias, experiment id and run code
    path("subreadset" / Segment) {
      q => {
        get {
          resolve(q).map(m => m) match {
            case lys: Seq[LimsYml] =>
              complete(if (lys.nonEmpty) 200 else 404, lys.toJson.prettyPrint)
            case t: Throwable => throw t // TODO: better error message for HTTP 500?
          }
        }
      }
    }

  /**
   * Resolves a dataset identifier to matching subreadsets
   */
  def resolve(q: String): Seq[LimsYml] = {
    Try(getByExperiment(q.toInt)) match {
      case Success(id :: ids) => getLimsYml(id :: ids)
      case _ =>
        Try(getByRunCode(q)) match {
          case Success(id :: ids) => getLimsYml(id :: ids)
          case _ =>
            Try(getByAlias(q)) match {
            case Success(id) => Seq(getLimsYml(id))
            case _ => Seq()
          }
        }
    }
  }
}