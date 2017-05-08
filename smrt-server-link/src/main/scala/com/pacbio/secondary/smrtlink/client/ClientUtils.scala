package com.pacbio.secondary.smrtlink.client

import java.io.File

import scala.math._
import spray.httpx.SprayJsonSupport
import spray.json._

import com.pacbio.secondary.analysis.DataSetFileUtils
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.reports.ReportModels._
import com.pacbio.common.client._
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.tools.timeUtils

trait ClientUtils extends timeUtils with DataSetFileUtils {

  import SmrtLinkJsonProtocols._

  def listFilesByExtension(f: File, ext: String): Array[File] = {
    if (! f.isDirectory) throw new IllegalArgumentException(s"${f.toString} is not a directory")
    f.listFiles.filter((fn) => fn.toString.endsWith(ext)).toArray ++ f.listFiles.filter(_.isDirectory).flatMap(d => listFilesByExtension(d, ext))
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
      println(s"  project id: ${job.projectId}")
      println(s"        path: ${job.path}")
      println(s"   jobTypeId: ${job.jobTypeId}")
      println(s"   is active: ${job.isActive}")
      println(s"   createdAt: ${job.createdAt}")
      println(s"   updatedAt: ${job.updatedAt}")
      println(s"    run time: $runTimeSec sec")
      println(s"  SL version: ${job.smrtlinkVersion.getOrElse("Unknown")}")
      println(s"  created by: ${job.createdBy.getOrElse("none")}")

      println(s"     comment: ${job.comment}")
      if (job.state == AnalysisJobStates.FAILED) {
        println(s"Error :\n ${job.errorMessage.getOrElse("Unknown")}")
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
