package com.pacbio.secondary.smrtlink.client

import java.util.UUID
import java.nio.file.{Paths, Path}

import scala.xml.XML

import spray.httpx.SprayJsonSupport
import spray.json._

import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.common.client._

trait ClientUtils {

  import SmrtLinkJsonProtocols._

  // FIXME this should probably return a DataSetMetaType
  def dsMetaTypeFromPath(path: Path): String = {
    val ds = scala.xml.XML.loadFile(path.toFile)
    ds.attributes("MetaType").toString
  }

  def dsUuidFromPath(path: Path): UUID = {
    val ds = scala.xml.XML.loadFile(path.toFile)
    val uniqueId = ds.attributes("UniqueId").toString
    java.util.UUID.fromString(uniqueId)
  }

  def dsNameFromMetadata(path: Path): String = {
    if (! path.toString.endsWith(".metadata.xml")) throw new Exception(s"File {p} lacks the expected extension (.metadata.xml)")
    val md = scala.xml.XML.loadFile(path.toFile)
    if (md.label != "Metadata") throw new Exception(s"The file ${path.toString} does not appear to be an RS II metadata XML")
    (md \ "Run" \ "Name").text
  }

  def printDataSetInfo(ds: DataSetMetaDataSet, asJson: Boolean = false): Int = {
    if (asJson) println(ds.toJson.prettyPrint) else {
      println("DATASET SUMMARY:")
      println(s"  id: ${ds.id}")
      println(s"  uuid: ${ds.uuid}")
      println(s"  name: ${ds.name}")
      println(s"  path: ${ds.path}")
      println(s"  numRecords: ${ds.numRecords}")
      println(s"  totalLength: ${ds.totalLength}")
      println(s"  jobId: ${ds.jobId}")
      println(s"  md5: ${ds.md5}")
      println(s"  createdAt: ${ds.createdAt}")
      println(s"  updatedAt: ${ds.updatedAt}")
    }
    0
  }

  def printJobInfo(job: EngineJob, asJson: Boolean = false): Int = {
    if (asJson) println(job.toJson.prettyPrint) else {
      println("JOB SUMMARY:")
      println(s"  id: ${job.id}")
      println(s"  uuid: ${job.uuid}")
      println(s"  name: ${job.name}")
      println(s"  state: ${job.state}")
      println(s"  path: ${job.path}")
      println(s"  jobTypeId: ${job.jobTypeId}")
      println(s"  createdAt: ${job.createdAt}")
      println(s"  updatedAt: ${job.updatedAt}")
      job.createdBy match {
        case Some(createdBy) => println(s"  createdBy: ${createdBy}")
        case _ => println("  createdBy: none")
      }
      println(s"  comment: ${job.comment}")
    }
    0
  }
}
