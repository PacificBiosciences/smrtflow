package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import spray.json._
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.analysis.jobs.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.techsupport.{
  TechSupportConstants,
  TechSupportUtils
}
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPointRecord
import org.apache.commons.io.FileUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobProtocols._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

case class TsSystemStatusBundleJobOptions(user: String,
                                          comment: String,
                                          name: Option[String],
                                          description: Option[String],
                                          projectId: Option[Int] = Some(
                                            JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions
    with TsJobValidationUtils {
  override def jobTypeId = JobTypeIds.TS_SYSTEM_STATUS
  override def toJob() = new TsSystemStatusBundleJob(this)

  /**
    * Validate that System was configured with a SMRT Link System ROOT.
    *
    */
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    val errorPrefix = "Unable to created System Status Bundle."
    config.smrtLinkSystemRoot match {
      case Some(path) =>
        if (Files.exists(path)) None
        else
          Option(InvalidJobOptionError(s"$errorPrefix Unable to find '$path'"))
      case None =>
        Some(InvalidJobOptionError(
          s"$errorPrefix System is not configured with a SMRT LINK root directory path."))
    }
  }

  override def resolveEntryPoints(
      dao: JobsDao): Seq[EngineJobEntryPointRecord] =
    Seq.empty[EngineJobEntryPointRecord]

}

class TsSystemStatusBundleJob(opts: TsSystemStatusBundleJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils
    with TsTgzUploadUtils
    with TsJobValidationUtils {
  type Out = PacBioDataStore

  def createRun(
      job: JobResourceBase,
      resultsWriter: JobResultsWriter,
      manifest: TsSystemStatusManifest,
      smrtLinkSystemRoot: Path,
      stdoutDataStoreFile: DataStoreFile): (PacBioDataStore, Path) = {

    resultsWriter.writeLine(s"TechSupport System Status Bundle Opts $opts")

    val outputTgz =
      job.path.resolve(TechSupportConstants.DEFAULT_TS_BUNDLE_TGZ)
    val outputDs = job.path.resolve("datastore.json")

    val manifestPath =
      job.path.resolve(TechSupportConstants.DEFAULT_TS_MANIFEST_JSON)

    FileUtils.writeStringToFile(manifestPath.toFile,
                                manifest.toJson.prettyPrint)

    // Should clean this up. There's inconsistencies where the the SL Root is used
    // and where smrt-link-system/userdata is used. I believe we only need userdata
    val smrtLinkUserData = smrtLinkSystemRoot.resolve("userdata")

    TechSupportUtils.writeSmrtLinkSystemStatusTgz(
      manifest.smrtLinkSystemId,
      smrtLinkUserData,
      outputTgz,
      manifest.user,
      manifest.smrtLinkSystemVersion,
      manifest.dnsName,
      manifest.comment
    )

    val totalSize = outputTgz.toFile.length()
    val totalSizeMB = totalSize / 1024.0 / 1024.0
    resultsWriter.writeLine(s"Total file size $totalSizeMB MB")

    resultsWriter.writeLine(s"Wrote TGZ to ${outputTgz.toAbsolutePath}")

    val createdAt = JodaDateTime.now()

    // Write the manifest for adding in debugging and to avoid having to unzip the tgz

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
      "TechSupport System Status",
      s"Tech Support System Status TGZ bundle for SL System Root $smrtLinkSystemRoot"
    )

    val ds =
      PacBioDataStore.fromFiles(Seq(dsFile, stdoutDataStoreFile, manifestDs))
    FileUtils.writeStringToFile(outputDs.toFile, ds.toJson.prettyPrint)

    resultsWriter.writeLine(
      s"Successfully create TS TGZ bundle ${manifest.id}")
    (ds, outputTgz)
  }

  private def validatePath(p: Path, msg: String): Future[Path] = {
    if (Files.exists(p)) Future.successful(p)
    else Future.failed(new UnprocessableEntityError(msg))
  }

  private def validateSmrtLinkSystemRoot(p: Option[Path]): Future[Path] = {
    p.map(px => validatePath(px, s"Unable to find SMRT Link System Root $px"))
      .getOrElse(Future.failed(
        new UnprocessableEntityError(NOT_CONFIGURED_MESSAGE_SL_ROOT)))

  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val startedAt = JodaDateTime.now()

    // This needs to be fixed
    val stdoutLog = resources.path.resolve(JobConstants.JOB_STDOUT)

    val manifestId = UUID.randomUUID()
    val manifest = TsSystemStatusManifest(
      manifestId,
      BundleTypes.SYSTEM_STATUS,
      1,
      JodaDateTime.now(),
      config.smrtLinkSystemId,
      Some(config.host),
      config.smrtLinkVersion,
      opts.user,
      Some(opts.comment)
    )

    val tx = for {
      stdoutDsFile <- addStdOutLogToDataStore(resources.jobId,
                                              dao,
                                              stdoutLog,
                                              opts.projectId)
      eveUrl <- validateEveUrl(config.externalEveUrl)
      systemRoot <- validateSmrtLinkSystemRoot(config.smrtLinkSystemRoot)
      (dataStore, tgzPath) <- Future.fromTry(
        Try(
          createRun(resources,
                    resultsWriter,
                    manifest,
                    systemRoot,
                    stdoutDsFile)))
      _ <- upload(eveUrl, config.eveApiSecret, tgzPath, resultsWriter)
    } yield dataStore

    convertTry(runAndBlock(tx, DEFAULT_MAX_UPLOAD_TIME),
               resultsWriter,
               startedAt,
               resources.jobId)
  }
}
