package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.Try
import org.joda.time.{DateTime => JodaDateTime}
import org.joda.time.format.DateTimeFormat
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  ExportJob,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJobUtils
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPoint
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

case class ExportSmrtLinkJobOptions(
    ids: Seq[IdAble],
    outputPath: Path,
    includeEntryPoints: Boolean,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID),
    submit: Option[Boolean] = Some(JobConstants.SUBMIT_DEFAULT_CORE_JOB),
    tags: Option[String] = None)
    extends ServiceJobOptions
    with ValidateJobUtils {

  private def MAX_NUMBER_JOBS = 5
  private def toValidateError(): String =
    s"Export SMRTLink Job(s) only supports <= $MAX_NUMBER_JOBS. Found ${ids.toSet.toList.length}."

  override def jobTypeId = JobTypeIds.EXPORT_JOBS
  override def toJob() = new ExportSmrtLinkJob(this)

  def validateJobIds(dao: JobsDao, jobIds: Seq[IdAble]): Future[Seq[UUID]] =
    Future.sequence(jobIds.map(dao.getJobById(_).map(_.uuid)))

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    val f: Future[Option[InvalidJobOptionError]] = for {
      _ <- validateOutputDir(outputPath)
      _ <- validateMaxItems(MAX_NUMBER_JOBS, ids, toValidateError())
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
    with CoreJobUtils
    with timeUtils {

  type Out = PacBioDataStore

  import com.pacbio.common.models.CommonModelImplicits._

  private def resolveJobs(dao: JobsDao,
                          jobIds: Seq[IdAble]): Future[Seq[EngineJob]] =
    Future.sequence(jobIds.map(dao.getJobById))

  private def toDataStoreZip(path: Path,
                             startedAt: JodaDateTime,
                             jobId: Int): DataStoreFile = {
    val endedAt = JodaDateTime.now()
    DataStoreFile(
      UUID.randomUUID(),
      s"pbscala::${opts.jobTypeId}",
      FileTypes.ZIP.fileTypeId,
      path.toFile.length(),
      startedAt,
      endedAt,
      path.toAbsolutePath.toString,
      isChunked = false,
      "ZIP file",
      s"ZIP file containing job $jobId"
    )
  }

  private def runOne(job: EngineJob,
                     outputPath: Path,
                     eps: Seq[BoundEntryPoint],
                     events: Seq[JobEvent]): Try[DataStoreFile] = {
    val startedAt = JodaDateTime.now()
    val now = DateTimeFormat.forPattern("yyyyddMM").print(startedAt)
    val zipName = s"ExportJob_${job.id}_$now.zip"
    val outputZipPath = outputPath.resolve(zipName)

    for {
      _ <- ExportJob(job, outputZipPath, eps, events)
      ds <- Try(toDataStoreZip(outputZipPath, startedAt, job.id))
    } yield ds
  }

  private def runOneF(job: EngineJob,
                      outputPath: Path,
                      eps: Seq[BoundEntryPoint],
                      events: Seq[JobEvent]): Future[DataStoreFile] =
    Future {
      blocking(Future.fromTry(runOne(job, outputPath, eps, events)))
    }.flatMap(identity)

  private def resolveAndUpdateDataSet(
      dao: JobsDao,
      jobDir: Path,
      e: EngineJobEntryPoint): Future[BoundEntryPoint] = {
    for {
      x <- dao.getDataSetMetaData(e.datasetUUID)
      p1 <- Future.successful(Paths.get(x.path))
      p2 <- updateDataSetandWriteToEntryPointsDir(p1, jobDir, dao)
      // FIXME. WTF was the ever added. This should have been explicitly extracted
      eid <- Future.successful(
        PbsmrtpipeConstants.metaTypeToEntryId(e.datasetType))
    } yield BoundEntryPoint(eid.getOrElse("unknown"), p2)

  }

  private def resolveAndUpdateDataSet2(
      dao: JobsDao,
      job: EngineJob,
      jobDir: Path): Future[Seq[BoundEntryPoint]] = {
    for {
      serviceEntryPoints <- dao.getJobEntryPoints(job.id)
      eps <- Future.sequence(serviceEntryPoints.map { e =>
        resolveAndUpdateDataSet(dao, jobDir, e)
      })
    } yield eps
  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val logFile = getStdOutLog(resources, dao)
    val startedAt = JodaDateTime.now()

    resultsWriter.writeLine(
      s"Starting export of ${opts.ids.length} jobs at ${startedAt.toString}")
    resultsWriter.writeLine(s"Job Export options: $opts")

    def writeFilesToDataStore(files: Seq[DataStoreFile]): PacBioDataStore = {
      val endedAt = JodaDateTime.now()
      val ds = PacBioDataStore(startedAt, endedAt, "0.1.0", files)
      val datastoreJson =
        resources.path.resolve(JobConstants.OUTPUT_DATASTORE_JSON)
      writeDataStore(ds, datastoreJson)
      resultsWriter.write(
        s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}")
      ds
    }

    def resolver(job: EngineJob): Future[Seq[BoundEntryPoint]] = {
      if (opts.includeEntryPoints)
        resolveAndUpdateDataSet2(dao, job, resources.path)
      else Future.successful(Seq.empty[BoundEntryPoint])
    }

    def resolveAll(job: EngineJob)
      : Future[(EngineJob, Seq[BoundEntryPoint], Seq[JobEvent])] =
      for {
        eps <- resolver(job)
        jobEvents <- dao.getJobEventsByJobId(job.id)
      } yield (job, eps, jobEvents)

    def fx2: Future[PacBioDataStore] =
      for {
        jobs <- resolveJobs(dao, opts.ids)
        jobEpEvents <- Future.sequence(jobs.map(resolveAll))
        dsFiles <- Future.sequence(
          jobEpEvents.map(xs => runOneF(xs._1, opts.outputPath, xs._2, xs._3)))
        ds <- Future.successful(writeFilesToDataStore(dsFiles ++ Seq(logFile)))
      } yield ds

    //FIXME This should be computed based in a more principled way
    val maxTimeOut: FiniteDuration = 1.minute * opts.ids.length
    convertTry(runAndBlock(fx2, maxTimeOut),
               resultsWriter,
               startedAt,
               resources.jobId)

  }
}
