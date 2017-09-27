package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.collection.mutable.ArrayBuffer
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.{Try, Failure, Success}

import org.joda.time.{DateTime => JodaDateTime}
import org.joda.time.format.DateTimeFormat

import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultWriter,
  ExportJob,
  AnalysisJobStates
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError

case class ExportSmrtLinkJobOptions(ids: Seq[IdAble],
                                    outputPath: Path,
                                    includeEntryPoints: Boolean,
                                    name: Option[String],
                                    description: Option[String],
                                    projectId: Option[Int] = Some(
                                      JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions
    with ValidateJobUtils {

  override def jobTypeId = JobTypeIds.EXPORT_JOBS
  override def toJob() = new ExportSmrtLinkJob(this)

  def validateJobIds(dao: JobsDao, jobIds: Seq[IdAble]): Future[Seq[UUID]] =
    Future.sequence(jobIds.map(dao.getJobById(_).map(_.uuid)))

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    val f: Future[Option[InvalidJobOptionError]] = for {
      _ <- validateOutputDir(outputPath)
      _ <- validateJobIds(dao, ids)
    } yield None

    val f2 = f.recover {
      case NonFatal(ex) =>
        Some(
          InvalidJobOptionError(
            s"Invalid ExportSmrtLinkJob options ${ex.getMessage}"))
    }

    Await.result(f2, DEFAULT_TIMEOUT)
  }
}

class ExportSmrtLinkJob(opts: ExportSmrtLinkJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils
    with timeUtils {

  type Out = PacBioDataStore

  import com.pacbio.common.models.CommonModelImplicits._

  private def resolveJobs(dao: JobsDao,
                          jobIds: Seq[IdAble]): Future[Seq[EngineJob]] =
    Future.sequence(jobIds.map(dao.getJobById(_)))

  private def runOne(job: EngineJob,
                     outputPath: Path,
                     eps: Seq[BoundEntryPoint]): Try[DataStoreFile] = {
    val startedAt = JodaDateTime.now()
    val now = DateTimeFormat.forPattern("yyyyddMM").print(startedAt)
    val zipName = s"ExportJob_${job.id}_${now}.zip"
    ExportJob(job, outputPath.resolve(zipName), eps) match {
      case Success(result) =>
        val endedAt = JodaDateTime.now()
        Try {
          DataStoreFile(
            UUID.randomUUID(),
            s"pbscala::${opts.jobTypeId}",
            FileTypes.ZIP.fileTypeId,
            result.nBytes,
            startedAt,
            endedAt,
            outputPath.resolve(zipName).toAbsolutePath.toString,
            isChunked = false,
            "ZIP file",
            s"ZIP file containing job ${job.id}"
          )
        }
      case Failure(err) =>
        logger.error(err.getMessage)
        Failure(err)
    }
  }

  private def resolveEntryPoints(
      dao: JobsDao,
      jobs: Seq[EngineJob]): Future[Seq[Seq[BoundEntryPoint]]] = {
    Future.sequence(jobs.map { job =>
      for {
        serviceEntryPoints <- dao.getJobEntryPoints(job.id)
        eps <- Future.sequence(serviceEntryPoints.map { e =>
          dao.getDataSetMetaData(e.datasetUUID).map { ds =>
            val entryId = PbsmrtpipeConstants.metaTypeToEntryId(e.datasetType)
            BoundEntryPoint(entryId.getOrElse("unknown"), Paths.get(ds.path))
          }
        })
      } yield eps
    })
  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    val fx = for {
      jobs <- resolveJobs(dao, opts.ids)
    } yield jobs
    val jobs: Seq[EngineJob] = Await.result(fx, opts.DEFAULT_TIMEOUT)

    val fx2 = for {
      entryPoints <- resolveEntryPoints(dao, jobs)
    } yield entryPoints
    val entryPoints: Seq[Seq[BoundEntryPoint]] = if (opts.includeEntryPoints) {
      Await.result(fx2, opts.DEFAULT_TIMEOUT)
    } else {
      jobs.map(_ => Seq.empty[BoundEntryPoint])
    }

    val startedAt = JodaDateTime.now()
    resultsWriter.writeLine(
      s"Starting export of ${opts.ids.length} jobs at ${startedAt.toString}")
    resultsWriter.writeLine(s"Job Export options: $opts")
    val datastoreJson = resources.path.resolve("datastore.json")

    val logPath = resources.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toMasterDataStoreFile(
      logPath,
      "Log file of the details of the Export DataSet Job job")

    val results = jobs.zip(entryPoints).map {
      case (job, eps) =>
        runOne(job, opts.outputPath, eps)
    }
    val dsFiles: Seq[DataStoreFile] = Seq(logFile) ++ results
      .filter(_.isSuccess)
      .map(_.toOption.get)
    val nErrors = results.count(_.isFailure == true)
    if (nErrors > 0) {
      val msg = s"One or more jobs could not be exported"
      resultsWriter.writeLine(msg)
      Left(
        ResultFailed(resources.jobId,
                     jobTypeId.toString,
                     msg,
                     computeTimeDeltaFromNow(startedAt),
                     AnalysisJobStates.FAILED,
                     host))
    } else {
      val endedAt = JodaDateTime.now()
      val ds = PacBioDataStore(startedAt, endedAt, "0.1.0", dsFiles)
      writeDataStore(ds, datastoreJson)
      resultsWriter.write(
        s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}")
      Right(ds)
    }
  }
}
