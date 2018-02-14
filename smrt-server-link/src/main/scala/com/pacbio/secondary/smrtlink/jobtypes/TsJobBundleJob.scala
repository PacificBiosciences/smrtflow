package com.pacbio.secondary.smrtlink.jobtypes

import java.net.URL
import java.util.UUID
import java.nio.file.{Path, Paths}

import com.pacbio.common.models.CommonModels._
import com.pacbio.common.models.CommonModelImplicits
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.techsupport.{
  TechSupportConstants,
  TechSupportUtils
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPointRecord

import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobProtocols._

import org.apache.commons.io.FileUtils
import spray.json._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/**
  * Created by mkocher on 8/17/17.
  */
case class TsJobBundleJobOptions(jobId: IdAble,
                                 user: String,
                                 comment: String,
                                 name: Option[String],
                                 description: Option[String],
                                 projectId: Option[Int] = Some(
                                   JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions
    with TsJobValidationUtils {

  import CommonModelImplicits._

  override def jobTypeId = JobTypeIds.TS_JOB
  override def toJob() = new TsJobBundleJob(this)

  override def resolveEntryPoints(dao: JobsDao) =
    Seq.empty[EngineJobEntryPointRecord]

  // Only Failure jobs can be submitted to TS for troubleshooting
  def onlyAllowFailed(job: EngineJob): Future[EngineJob] = {
    if (job.hasFailed) Future.successful(job)
    else
      Future.failed(throw new UnprocessableEntityError(
        s"Can only can send failed jobs. Job ${job.id} type:${job.jobTypeId} state:${job.state}"))
  }

  def getJob(dao: JobsDao): Future[EngineJob] = {
    for {
      engineJob <- dao.getJobById(jobId)
      validJob <- onlyAllowFailed(engineJob)
    } yield validJob
  }

  /**
    * Validate the job is found and is in the FAILED state.
    *
    */
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {

    val f = for {
      _ <- validateEveUrl(config.externalEveUrl)
      job <- getJob(dao)
    } yield job

    Try(Await.result(f, DEFAULT_TIMEOUT)) match {
      case Success(_) => None
      case Failure(ex) =>
        Some(InvalidJobOptionError(s"Invalid option ${ex.getMessage}"))
    }
  }

}

class TsJobBundleJob(opts: TsJobBundleJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils
    with TsTgzUploadUtils {
  type Out = PacBioDataStore

  def createBundle(
      failedJobPath: Path,
      job: JobResourceBase,
      resultsWriter: JobResultsWriter,
      manifest: TsJobManifest,
      stdoutDataStoreFile: DataStoreFile): (PacBioDataStore, Path) = {
    resultsWriter.writeLine(s"TechSupport Bundle Opts $opts")

    val outputTgz =
      job.path.resolve(TechSupportConstants.DEFAULT_TS_BUNDLE_TGZ)
    val outputDs = job.path.resolve("datastore.json")

    val manifestPath =
      job.path.resolve(TechSupportConstants.DEFAULT_TS_MANIFEST_JSON)

    FileUtils.writeStringToFile(manifestPath.toFile,
                                manifest.toJson.prettyPrint)

    TechSupportUtils.writeJobBundleTgz(failedJobPath, manifest, outputTgz)

    val totalSizeMB = outputTgz.toFile.length / 1024.0 / 1024.0
    resultsWriter.writeLine(s"Total file size $totalSizeMB MB")

    // Create DataStore
    val createdAt = JodaDateTime.now()

    val manifestDs = DataStoreFile(
      UUID.randomUUID(),
      "ts-manifest-0",
      FileTypes.JSON.fileTypeId,
      manifestPath.toFile.length(),
      createdAt,
      createdAt,
      manifestPath.toAbsolutePath.toString,
      false,
      "TS System Status Manifest",
      "Tech Support System Status Manifest"
    )

    val dsFile = DataStoreFile(
      UUID.randomUUID(),
      "ts-bundle-job-0",
      FileTypes.TS_TGZ.fileTypeId,
      outputTgz.toFile.length(),
      createdAt,
      createdAt,
      outputTgz.toAbsolutePath.toString,
      isChunked = false,
      s"TS Job ${manifest.jobTypeId} id:${manifest.jobId} Bundle",
      s"TechSupport Bundle for Job type:${manifest.jobTypeId} id: ${manifest.jobTypeId}"
    )

    val ds =
      PacBioDataStore.fromFiles(Seq(dsFile, stdoutDataStoreFile, manifestDs))
    FileUtils.writeStringToFile(outputDs.toFile, ds.toJson.prettyPrint)

    resultsWriter.writeLine(
      s"Successfully created TS TGZ bundle ${manifest.id}")
    (ds, outputTgz)
  }

  /// "host" is a bit unclear here. This is propagated from the dnsName
  def toTsJobManifest(uuid: UUID,
                      engineJob: EngineJob,
                      dnsName: Option[String],
                      smrtLinkVersion: Option[String],
                      smrtLinkSystemId: UUID): TsJobManifest = TsJobManifest(
    uuid,
    BundleTypes.JOB,
    1,
    JodaDateTime.now(),
    smrtLinkSystemId,
    dnsName,
    smrtLinkVersion,
    opts.user,
    Some(opts.comment),
    engineJob.jobTypeId,
    engineJob.id
  )

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val startedAt = JodaDateTime.now()

    // This needs to be fixed
    val stdoutLog = resources.path.resolve(JobConstants.JOB_STDOUT)

    // Add stdout/log proactively so errors are exposed
    val tx = for {
      stdoutDsFile <- addStdOutLogToDataStore(resources.jobId,
                                              dao,
                                              stdoutLog,
                                              opts.projectId)
      eveUrl <- opts.validateEveUrl(config.externalEveUrl)
      failedJob <- opts.getJob(dao)
      manifest <- Future.successful(
        toTsJobManifest(resources.jobId,
                        failedJob,
                        Some(config.host),
                        config.smrtLinkVersion,
                        config.smrtLinkSystemId))
      (dataStore, tgzPath) <- Future.fromTry(
        Try(
          createBundle(Paths.get(failedJob.path),
                       resources,
                       resultsWriter,
                       manifest,
                       stdoutDsFile)))
      _ <- upload(eveUrl, config.eveApiSecret, tgzPath, resultsWriter)
    } yield dataStore

    convertTry(runAndBlock(tx, DEFAULT_MAX_UPLOAD_TIME),
               resultsWriter,
               startedAt,
               resources.jobId)
  }
}
