package com.pacbio.secondary.lims.services

import java.io.{BufferedReader, StringReader}
import java.nio.file.{Files, Paths}
import java.util.UUID

import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.lims.database.Database
import com.pacificbiosciences.pacbiodatasets.SubreadSet
import spray.http.MultipartFormData
import spray.routing.HttpService

import scala.collection.mutable
import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import spray.json._
import DefaultJsonProtocol._
import com.pacbio.secondary.lims.JsonProtocol._


/**
 * Imports all needed LIMS information for doing lookups and a resolution service
 *
 * This information currently comes from lims.yml and the related .subreaddata.xml file in found in
 * the same directory.
 */
trait ImportLims extends HttpService with LookupSubreadset {
  this: Database  =>

  implicit def executionContext = actorRefFactory.dispatcher

  val importLimsRoutes =
    // lims.yml files must be posted to the server
    pathPrefix("import") {
      post {
        entity(as[MultipartFormData]) {
          formData => {
            val uploadedFile = formData.fields.head.entity.data.toByteArray
            complete(
              Future(loadData(uploadedFile)))
          }
        }
      }
    }

  /**
   * Converts the YML to a Map.
   */
  def loadData(bytes: Array[Byte]): String = {
    // loads the lims.yml file results -- really should be loading per dir, not lims.yml
    val sr = new StringReader(new String(bytes))
    val br = new BufferedReader(sr)
    var l = br.readLine()
    val m = mutable.HashMap[String, String]()
    while (l != null) {
      val all = l.split(":[ ]+")
      val (k, v) = (all(0), all(1).stripPrefix("'").stripSuffix("'").trim)
      m.put(k, v)
      l = br.readLine()
    }

    // need the path in order to parse the UUID from .subreadset.xml
    m.get("path") match {
      case Some(p) => loadData(subreadset(p), m)
      case None => "No path in lims.yml file. Can't attempt UUID lookup"
    }
  }

  def loadData(s: Option[SubreadSet], ly: mutable.HashMap[String, String]) : String = {
    // collection info
    val (uuid, json) = s match {
      case Some(subread) => {
        val c = subread.getDataSetMetadata.getCollections.getCollectionMetadata.get(0)
        (subread.getUniqueId,
         Map[String, Any](
           "path" -> ly("path"),
           "pa_version" -> c.getSigProcVer,
           "ics_version" -> c.getInstCtrlVer,
           "well" -> c.getWellSample.getWellName,
           "context" -> c.getContext,
           "created_at" -> subread.getCreatedAt.toString,
           "inst_name" -> c.getInstrumentName,
           "instid" -> c.getInstrumentId
         ).toJson)
      }
      case None => (null, "{}".toJson)
    }
    val expid = ly("expcode").toInt
    val runcode = ly("runcode")

    // TODO: should be smarter about error handling here and not making alias if setSubread() fails
    Try(if (uuid != null) setAlias(makeShortcode(UUID.fromString(uuid)), UUID.fromString(uuid), LimsTypes.limsSubreadSet))
    setSubread(UUID.fromString(uuid), expid, runcode, json)
  }

  def makeShortcode(uuid: UUID): String = uuid.toString.substring(0, 6)
}

/**
 * Trait required for abstracting the UUID file lookup for prod vs testing
 */
trait LookupSubreadset {
  def subreadset(path: String): Option[SubreadSet]
}

trait FileLookupSubreadset {
  def subreadset(path: String): Option[SubreadSet] = {
    Try {
      val p = Paths.get(path.stripPrefix("file://"))
      (for (v <- Files.newDirectoryStream(p).asScala
            if v.toString.endsWith("subreadset.xml"))
        yield DataSetLoader.loadSubreadSet(v)).head
    } match {
      case Success(v) => Some(v)
      case Failure(t) => None
    }
  }
}