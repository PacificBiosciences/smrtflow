package com.pacbio.secondary.smrtlink.client

import java.util.UUID
import java.nio.file.{Path, Paths}

import scala.xml.{Elem,XML}
import scala.math._
import scala.util.{Try,Failure,Success}
import spray.httpx.SprayJsonSupport
import spray.json._


import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.reports.ReportModels._
import com.pacbio.common.client._
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.tools.timeUtils

trait ClientUtils extends timeUtils{

  import SmrtLinkJsonProtocols._

  private def parseXml(path: Path) = {
    Try { scala.xml.XML.loadFile(path.toFile) } match {
      case Success(x) => x
      case Failure(err) => throw new IllegalArgumentException(s"Couldn't parse ${path.toString} as an XML file: ${err.getMessage}")
    }
  }

  private def getAttribute(e: Elem, attr: String): String = {
    Try { e.attributes(attr).toString } match {
      case Success(a) => a
      case Failure(err) => throw new Exception(s"Can't retrieve $attr attribute from XML: ${err.getMessage}.  Please make sure this is a valid PacBio DataSet XML file.")
    }
  }

  // FIXME this should probably return a DataSetMetaType
  def dsMetaTypeFromPath(path: Path): String =
    getAttribute(parseXml(path), "MetaType")

  def dsUuidFromPath(path: Path): UUID =
    java.util.UUID.fromString(getAttribute(parseXml(path), "UniqueId"))

  def dsNameFromMetadata(path: Path): String = {
    if (! path.toString.endsWith(".metadata.xml")) throw new Exception(s"File {p} lacks the expected extension (.metadata.xml)")
    val md = scala.xml.XML.loadFile(path.toFile)
    if (md.label != "Metadata") throw new Exception(s"The file ${path.toString} does not appear to be an RS II metadata XML")
    (md \ "Run" \ "Name").text
  }

  def printDataSetInfo(ds: DataSetMetaDataSet, asJson: Boolean = false): Int = {
    if (asJson) println(ds.toJson.prettyPrint) else {
      if (ds.isActive) println("DATASET SUMMARY:")
      else println("DATASET SUMMARY (INACTIVE/DELETED):")
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

  def printJobInfo(job: EngineJob,
                   asJson: Boolean = false,
                   dumpJobSettings: Boolean = false): Int = {

    val runTimeSec = computeTimeDelta(job.updatedAt, job.createdAt)

    if (dumpJobSettings) {
      println(job.jsonSettings.parseJson.prettyPrint)
    } else if (asJson) {
      println(job.toJson.prettyPrint)
    } else {
      if (job.isActive) println("JOB SUMMARY:")
      else println("JOB SUMMARY (INACTIVE/DELETED):")
      println(s"          id: ${job.id}")
      println(s"        uuid: ${job.uuid}")
      println(s"        name: ${job.name}")
      println(s"       state: ${job.state}")
      println(s"        path: ${job.path}")
      println(s"   jobTypeId: ${job.jobTypeId}")
      println(s"   createdAt: ${job.createdAt}")
      println(s"   updatedAt: ${job.updatedAt}")
      println(s"    run time: $runTimeSec sec")
      println(s"  SL version: ${job.smrtlinkVersion.getOrElse("Unknown")}")
      println(s"  created by: ${job.createdBy.getOrElse("none")}")

      println(s"     comment: ${job.comment}")
      if (job.state == AnalysisJobStates.FAILED) {
        println(s"Error :\n ${job.errorMessage}")
      }
    }
    0
  }

  def printProjectInfo(project: FullProject): Int = {
    println("PROJECT SUMMARY:")
    println(s"  id: ${project.id}")
    println(s"  name: ${project.name}")
    println(s"  description: ${project.description}")
    println(s"  createdAt: ${project.createdAt}")
    println(s"  updatedAt: ${project.updatedAt}")
    println(s"  datasets: ${project.datasets.size}")
    println(s"  members: ${project.members.size}")
    0
  }

  def printTable(table: Seq[Seq[String]], headers: Seq[String]): Int = {
    val columns = table.transpose
    val widths = (columns zip headers).map{ case (col, header) =>
      max(header.length, col.map(_.length).reduceLeft(_ max _))
    }
    val mkline = (row: Seq[String]) => (row zip widths).map{ case (c,w) => c.padTo(w, ' ') }
    println(mkline(headers).mkString(" "))
    table.foreach(row => println(mkline(row).mkString(" ")))
    0
  }

  def showReportAttributes(r: Report, prefix: String = ""): Int = {
    println(s"${prefix}${r.title}:")
    r.attributes.foreach { a =>
      println(s"  ${prefix}${a.name} = ${a.value}")
    }
    0
  }
}
