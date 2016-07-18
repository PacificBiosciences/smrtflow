package com.pacbio.secondary.lims.services

import java.io.{BufferedReader, StringReader}
import java.nio.file.{Files, Paths}

import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.Database
import spray.http.MultipartFormData
import spray.routing.HttpService

import scala.collection.mutable
import scala.concurrent.Future
import scala.collection.JavaConverters._


/**
 * Imports all needed LIMS information for doing lookups and a resolution service
 *
 * This information currently comes from lims.yml and the related .subreaddata.xml file in found in
 * the same directory.
 */
trait ImportLims extends HttpService with LookupSubreadsetUuid {
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
    val sr = new StringReader(new String(bytes))
    val br = new BufferedReader(sr)
    var l = br.readLine()
    val m = mutable.HashMap[String, String]()
    while (l != null) {
      val all = l.split(":[ ]+")
      val (k, v) = (all(0), all(1).stripPrefix("'").stripSuffix("'"))
      m.put(k, v)
      l = br.readLine()
    }

    // need the path in order to parse the UUID from .subreadset.xml
    val uuid: String = lookupUuid(m.get("path").get)

    // now set the tuple related to the experiment
    loadData(uuid, m)
  }

  def loadData(uuid: String, m: mutable.HashMap[String, String]) : String = {
    setLimsYml(
      LimsYml(
        uuid = uuid,
        expcode = m.get("expcode").get.toInt,
        runcode = m.get("runcode").get,
        path = m.get("path").get,
        user = m.get("user").get,
        uid = m.get("uid").get,
        tracefile = m.get("tracefile").get,
        description = m.get("description").get,
        wellname = m.get("wellname").get,
        cellbarcode = m.get("cellbarcode").get,
        cellindex = m.get("cellindex").get.toInt,
        seqkitbarcode = m.get("seqkitbarcode").get,
        colnum = m.get("colnum").get.toInt,
        samplename = m.get("samplename").get,
        instid = m.getOrElse("instid", null).toInt)
    )
  }
}

/**
 * Trait required for abstracting the UUID file lookup for prod vs testing
 */
trait LookupSubreadsetUuid {
  def lookupUuid(path: String): String
}

trait FileLookupSubreadsetUuid {
  def lookupUuid(path: String): String = {
    val p = Paths.get(path.stripPrefix("file://"))
    val matches  =
      for (v <- Files.newDirectoryStream(p).asScala
           if v.toString.endsWith("subreadset.xml"))
        yield DataSetLoader.loadSubreadSet(v).getUniqueId
    matches.headOption match {
      case Some(s) => s
      case t => ""
    }
  }
}