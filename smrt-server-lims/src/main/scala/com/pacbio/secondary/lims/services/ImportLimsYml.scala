package com.pacbio.secondary.lims.services

import java.io.{BufferedReader, StringReader}

import com.pacbio.secondary.lims.LimsYml
import com.pacbio.secondary.lims.database.DatabaseService
import spray.http.MultipartFormData
import spray.routing.HttpService
import kamon.spray.KamonTraceDirectives.traceName

import scala.collection.mutable
import scala.concurrent.Future

/**
 * Created by jfalkner on 6/30/16.
 */
trait ImportLimsYml extends HttpService {
  this: DatabaseService =>

  // we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
  implicit def executionContext = actorRefFactory.dispatcher

  // TODO: lots of code below to simply map PUT to an import...simplify this?
  val importLimsYmlRoutes =
    // lims.yml files must be posted to the server
    pathPrefix("import") {
      post {
        traceName("ImportLimsYml") {
          entity(as[MultipartFormData]) {
            formData => {
              val uploadedFile = formData.fields.head.entity.data.toByteArray
              complete(
                Future(loadData(uploadedFile)))
            }
          }
        }
      }
    }

  /**
   * Converts the YML to a Map.
   *
   * @param bytes
   * @return
   */
  def loadData(bytes: Array[Byte]): String = {
    val sr = new StringReader(new String(bytes))
    val br = new BufferedReader(sr)
    var l = br.readLine()
    val m = mutable.HashMap[String, String]()
    while (l != null) {
      val all = l.split(":[ ]+")
      val (k, v) = (all(0), all(1))
      m.put(k, v)
      l = br.readLine()
    }
    loadData(m)
  }

  def loadData(m: mutable.HashMap[String, String]) : String = {
    setLimsYml(
      LimsYml(
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