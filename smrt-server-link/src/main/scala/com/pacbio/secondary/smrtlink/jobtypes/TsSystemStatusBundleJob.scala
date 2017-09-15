package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultWriter
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{
  TsSystemStatusBundleOptions => OldTsSystemStatusBundleOptions
}
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPointRecord

case class TsSystemStatusBundleJobOptions(user: String,
                                          comment: String,
                                          name: Option[String],
                                          description: Option[String],
                                          projectId: Option[Int] = Some(
                                            JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
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
    extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val manifestId = UUID.randomUUID()
    // A little sloppy here. Validate should already be called.
    val smrtLinkSystemRoot: Path = config.smrtLinkSystemRoot.get

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

    val oldOpts = OldTsSystemStatusBundleOptions(smrtLinkSystemRoot, manifest)
    oldOpts.toJob.run(resources, resultsWriter)
  }
}
