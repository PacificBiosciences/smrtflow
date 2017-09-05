
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
import com.pacbio.secondary.smrtlink.analysis.jobs.{InvalidJobOptionError, JobResultWriter, ExportJob}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MockJobUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError


case class ExportAnalysisJobOptions(ids: Seq[IdAble],
                                    outputPath: Path,
                                    name: Option[String],
                                    description: Option[String],
                                    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions with ValidateJobUtils {

  override def jobTypeId = JobTypeIds.EXPORT_JOBS
  override def toJob() = new ExportAnalysisJob(this)

  def validateJobIds(dao: JobsDao, jobIds: Seq[IdAble]): Future[Seq[UUID]] =
    Future.sequence(jobIds.map(dao.getJobById(_).map(_.uuid)))

  override def validate(dao: JobsDao, config: SystemJobConfig):
                        Option[InvalidJobOptionError] = {
    val f: Future[Option[InvalidJobOptionError]] = for {
      _ <- validateOutputDir(outputPath)
      _ <- validateJobIds(dao, ids)
    } yield None

    val f2 = f.recover {case NonFatal(ex) => Some(InvalidJobOptionError(s"Invalid ExportAnalysisJob options ${ex.getMessage}"))}

    Await.result(f2, DEFAULT_TIMEOUT)
  }
}

class ExportAnalysisJob(opts: ExportAnalysisJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils {

  type Out = PacBioDataStore

  import com.pacbio.common.models.CommonModelImplicits._

  def resolveJobs(dao: JobsDao, jobIds: Seq[IdAble]): Future[Seq[EngineJob]] =
    Future.sequence(jobIds.map(dao.getJobById(_)))

  def runOne(job: EngineJob, outputPath: Path): Try[DataStoreFile] = {
    val startedAt = JodaDateTime.now()
    val now = DateTimeFormat.forPattern("yyyyddMM").print(startedAt)
    val zipName = s"ExportJob_${job.id}_${now}.zip"
    ExportJob(job, outputPath.resolve(zipName)) match {
      case Success(result) =>
        val endedAt = JodaDateTime.now()
        Try { DataStoreFile(
          UUID.randomUUID(),
          s"pbscala::${opts.jobTypeId}",
          FileTypes.ZIP.fileTypeId,
          result.nBytes,
          startedAt,
          endedAt,
          outputPath.resolve(zipName).toAbsolutePath.toString,
          isChunked = false,
          "ZIP file",
          s"ZIP file containing job ${job.id}") }
      case Failure(err) => Failure(err)
    }
  }

  override def run(resources: JobResourceBase,
                   resultsWriter: JobResultWriter,
                   dao: JobsDao,
                   config: SystemJobConfig):
                   Either[ResultFailed, PacBioDataStore] = {
    val fx = for {
      jobs <- resolveJobs(dao, opts.ids)
      paths <- Future.successful(jobs.map(runOne(_, opts.outputPath)))
    } yield jobs

    val jobs: Seq[EngineJob] = Await.result(fx, opts.DEFAULT_TIMEOUT)

    val startedAt = JodaDateTime.now()
    resultsWriter.writeLine(s"Starting export of ${opts.ids.length} jobs at ${startedAt.toString}")
    resultsWriter.writeLine(s"Job Export options: $opts")
    val datastoreJson = resources.path.resolve("datastore.json")

    val logPath = resources.path.resolve(JobConstants.JOB_STDOUT)
    //val logFile = toMasterDataStoreFile(logPath, "Log file of the details of the Export DataSet Job job")

    var nErrors = 0
    var dsFiles = new ArrayBuffer[DataStoreFile]()//logFile)
    jobs.foreach { job =>
      runOne(job, opts.outputPath) match {
        case Success(f) => dsFiles += f
        case Failure(err) =>
          resultsWriter.writeLine(s"Export of job ${job.id} failed:")
          resultsWriter.writeLine(s"  ${err.getMessage}")
          nErrors += 1
      }
    }
    val endedAt = JodaDateTime.now()
    val ds = PacBioDataStore(startedAt, endedAt, "0.1.0", dsFiles.toList)
    writeDataStore(ds, datastoreJson)
    resultsWriter.write(s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}")
    Right(ds)
  }
}
