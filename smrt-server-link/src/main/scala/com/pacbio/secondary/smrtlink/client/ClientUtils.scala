package com.pacbio.secondary.smrtlink.client

import java.nio.file.{Path, Paths}
import java.io.File

import scala.math._
import scala.util.{Try, Failure, Success}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels._
import com.pacbio.common.models.Constants
import com.pacbio.common.semver.SemVersion
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils

trait ClientUtils extends timeUtils with DataSetFileUtils {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  def listFilesByExtension(f: File, ext: String): Array[File] = {
    if (!f.isDirectory)
      throw new IllegalArgumentException(s"${f.toString} is not a directory")
    f.listFiles
      .filter((fn) => fn.toString.endsWith(ext))
      .toArray ++ f.listFiles
      .filter(_.isDirectory)
      .flatMap(d => listFilesByExtension(d, ext))
  }

  def toDataSetInfoSummary(ds: DataSetMetaDataSet): String = {
    val active = if (ds.isActive) "" else "(INACTIVE/SOFT-DELETED)"
    s"""
      |*DATASET SUMMARY* $active
      |          id: ${ds.id}
      |        uuid: ${ds.uuid}
      |        name: ${ds.name}
      |  numRecords: ${ds.numRecords}
      | totalLength: ${ds.totalLength}
      |   createdAt: ${ds.createdAt}
      |   updatedAt: ${ds.updatedAt}
      |  importedAt: ${ds.importedAt}
      |   createdBy: ${ds.createdBy.getOrElse("")}
      |        tags: ${ds.tags}
      |    isActive: ${ds.isActive}
      | parent UUID: ${ds.parentUuid.getOrElse("")}
      | numchildren: ${ds.numChildren}
      |       jobId: ${ds.jobId}
      |   projectId: ${ds.projectId}
      |        path: ${ds.path}
    """.stripMargin
  }

  /**
    * Generate a Human readable summary of an Engine Job
    *
    * @param job Engine Job
    * @return
    */
  def toJobSummary(job: EngineJob): String = {
    val header = if (job.isActive) "" else "(INACTIVE/DELETED)"
    val runTimeSec = computeTimeDelta(job.updatedAt, job.createdAt)
    val body =
      s"""
        |*JOB SUMMARY* $header
        |            id: ${job.id}
        |          uuid: ${job.uuid}
        |          name: ${job.name}
        |         state: ${job.state}
        |    project id: ${job.projectId}
        |     jobTypeId: ${job.jobTypeId}
        |     is active: ${job.isActive}
        |     createdAt: ${job.createdAt}
        |     updatedAt: ${job.updatedAt}
        |  jobUpdatedAt: ${job.jobUpdatedAt}
        |      run time: $runTimeSec sec
        |    SL version: ${job.smrtlinkVersion.getOrElse("Unknown")}
        |    created by: ${job.createdBy.getOrElse("none")}
        |       comment: ${job.comment}
        |          tags: ${job.tags}
        |          path: ${job.path}
      """.stripMargin

    val errorMessage =
      if (AnalysisJobStates.FAILURE_STATES contains job.state) {
        job.errorMessage.getOrElse("Unknown")
      } else { "" }

    Seq(header, body, errorMessage).reduce(_ + "\n" + _)
  }

  def formatJobInfo(job: EngineJob,
                    asJson: Boolean = false,
                    dumpJobSettings: Boolean = false): String = {
    if (dumpJobSettings) {
      job.jsonSettings.parseJson.prettyPrint
    } else if (asJson) {
      job.toJson.prettyPrint
    } else {
      toJobSummary(job)
    }
  }

  protected def toJobsSummary(engineJobs: Seq[EngineJob],
                              asJson: Boolean = false): String = {
    if (asJson) {
      engineJobs.toJson.prettyPrint
    } else {
      val table = engineJobs
        .sortBy(_.id)
        .reverse
        .map(job =>
          Seq(
            job.id.toString,
            job.state.toString,
            job.name,
            job.createdAt.toString(),
            job.jobCompletedAt.map(_.toString()).getOrElse(""),
            job.getJobRunTime().map(_.toString()).getOrElse(""),
            job.uuid.toString,
            job.createdBy.getOrElse(""),
            job.tags
        ))
      toTable(table,
              Seq("ID",
                  "State",
                  "Name",
                  "CreatedAt",
                  "CompletedAt",
                  "RunTime",
                  "UUID",
                  "CreatedBy",
                  "Tags"))
    }
  }

  def toProjectSummary(project: FullProject): String =
    s"""
      |PROJECT SUMMARY:
      |  id: ${project.id}
      |  name: ${project.name}
      |  description: ${project.description}
      |  createdAt: ${project.createdAt}
      |  updatedAt: ${project.updatedAt}
      |  datasets: ${project.datasets.size}
      |  members: ${project.members.size}
     """.stripMargin

  private def getOrUnknown(s: Option[String]): String = s.getOrElse("unknown")
  private def getOrNA(date: Option[JodaDateTime]): String =
    date.map(_.toString).getOrElse("N/A")

  def toRunSummary(run: Run,
                   asJson: Boolean = false,
                   asXml: Boolean = false): String = {
    if (asJson) {
      run.summarize.toJson.prettyPrint.toString
    } else if (asXml) {
      run.dataModel
    } else {
      s"""
        |RUN SUMMARY:
        |                 id: ${run.uniqueId}
        |               name: ${run.name}
        |           reserved: ${run.reserved}
        |             status: ${run.status}
        |            created: ${getOrNA(run.createdAt)}
        |         created by: ${getOrUnknown(run.createdBy)}
        |            started: ${getOrNA(run.startedAt)}
        |            context: ${getOrUnknown(run.context)}
        |         instrument: ${getOrUnknown(run.instrumentName)}
        |      instrument SW: ${getOrUnknown(run.instrumentSwVersion)}
        |  # completed cells: ${run.numCellsCompleted}
        |          completed: ${getOrNA(run.completedAt)}
      """.stripMargin
    }
  }

  // Create a Table as String. This should be better model with a streaming
  // solution that passes in the "printer"
  def toTable(table: Seq[Seq[String]], headers: Seq[String]): String = {

    val columns = table.transpose
    val widths = (columns zip headers).map {
      case (col, header) =>
        max(header.length, col.map(_.length).max)
    }

    val mkline = (row: Seq[String]) =>
      (row zip widths).map { case (c, w) => c.padTo(w, ' ') }

    mkline(headers).mkString(" ") ++ "\n" ++
      table
        .map(row => mkline(row).mkString(" ") + "\n")
        .reduceLeftOption(_ + _)
        .getOrElse("NO DATA FOUND")
  }

  def formatReportAttributes(r: Report, prefix: String = ""): String = {
    (Seq(s"${prefix}${r.title}:") ++ r.attributes.map { a =>
      s"  ${prefix}${a.name} = ${a.value}"
    }).mkString("\n")
  }

  def formatDataStoreFiles(files: Seq[DataStoreServiceFile],
                           basePath: Option[Path] = None): String = {
    def toPath(p: String) = {
      val path = Paths.get(p)
      basePath
        .map { bp =>
          if (path.startsWith(bp)) bp.relativize(path) else path
        }
        .getOrElse(path)
    }
    val rows =
      files.map(f => Seq(f.name, f.fileTypeId, toPath(f.path).toString))
    toTable(rows, Seq("Name", "Filetype", "Path"))
  }

  /**
    * Check V1 gte V2 and return v1
    *
    * @param v1
    * @param v2
    * @return
    */
  private def versionGte(v1: SemVersion, v2: SemVersion): Future[SemVersion] = {
    if (v1.gte(v2)) Future.successful(v1)
    else
      Future.failed(throw new Exception(
        s"Incompatible versions ${v1.toSemVerString()} < ${v2.toSemVerString}"))
  }

  def isVersionGte(status: ServiceStatus, v: SemVersion): Future[SemVersion] = {
    for {
      remoteSystemVersion <- Future.successful(
        SemVersion.fromString(status.version))
      validatedRemoteSystemVersion <- versionGte(remoteSystemVersion, v)
    } yield validatedRemoteSystemVersion
  }

  def isVersionGteSystemVersion(status: ServiceStatus): Future[SemVersion] =
    isVersionGte(status, SemVersion.fromString(Constants.SMRTFLOW_VERSION))

}

trait ClientAppUtils extends DaoFutureUtils with LazyLogging {
  // These are the ONLY place that should have a blocking call
  // and explicit case match to Success/Failure handing for Try
  def executeBlockAndSummary(fx: Future[String],
                             timeout: FiniteDuration): Int = {
    executeAndSummary(Try(Await.result(fx, timeout)))
  }

  def executeAndSummary(tx: Try[String]): Int = {
    tx match {
      case Success(sx) =>
        println(sx)
        0
      case Failure(ex) =>
        logger.error(s"${ex.getMessage}")
        System.err.println(s"${ex.getMessage} $ex")
        1
    }
  }
}
