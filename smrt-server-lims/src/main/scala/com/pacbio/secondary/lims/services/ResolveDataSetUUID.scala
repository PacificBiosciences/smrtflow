package com.pacbio.secondary.lims.services

import com.pacbio.secondary.lims.database.Database
import spray.routing.HttpService

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

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
            implicit def executionContext = actorRefFactory.dispatcher
            complete(Future(resolve(q)))
          }
        }
      }
    }

  /**
   * Resolves a dataset UUID
   *
   * 1. Attempt exact match
   * 2. Attempt exact prefix match (LIKE prefix%)
   * 3. Translated Job ID -> DataSet UUID
   *
   */
  def resolve(q: String): String = {
    // attempt exact match to leverage DB indexing
    Try (getByUUID(q)) match {
      case Success(uuid) => uuid
      case Failure(t) => {
        // attempt LIKE for a prefix match. slower due to table scan
        Try(getByUUIDPrefix(q)) match {
          case Success(uuid) => uuid
          case Failure (t) => throw t // try the job ID lookup?
        }
      }
    }
  }
}