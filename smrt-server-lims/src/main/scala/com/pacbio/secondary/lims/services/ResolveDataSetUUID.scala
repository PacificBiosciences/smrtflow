package com.pacbio.secondary.lims.services

import java.io.{BufferedReader, StringReader}

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.Database
import spray.http.MultipartFormData
import spray.routing.HttpService

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

object ResolveDataSetUUID {
  val uuidPrefixTree = null
  val jobToUUIDBucket = null
}

/**
 * Created by jfalkner on 6/30/16.
 */
trait ResolveDataSetUUID extends HttpService {

  val resolveRoutes =
  // lims.yml files must be posted to the server
    pathPrefix("resolve") {
      get {
        parameters('q) {
          q => {
            // we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
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

    // attempt to use the in-memory cache


    val db = Database.get()
    // attempt exact match to leverage DB indexing
    Try (db.getByUUID(q)) match {
      case Success(uuid) => uuid
      case Failure(t) => {
        // attempt LIKE for a prefix match. slower due to table scan
        Try(db.getByUUIDPrefix(q)) match {
          case Success(uuid) => uuid
          case Failure (t) => throw t // try the job ID lookup?
        }
      }
    }
  }
}