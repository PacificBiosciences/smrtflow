package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.Path
import java.nio.file.Files
import java.util.UUID

import spray.json._
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.common.utils.TarGzUtil
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.{BaseCoreJob, BaseJobOptions, JobResultWriter}
import com.pacbio.secondary.analysis.jobs.SecondaryJobProtocols._
import com.pacbio.secondary.analysis.techsupport.{TechSupportConstants, TechSupportUtils}
import org.apache.commons.io.FileUtils



case class TsSystemStatusBundleOptions(smrtLinkSystemRoot: Path, manifest: TsSystemStatusManifest) extends BaseJobOptions {
  // This is just to adhere to the interface
  val projectId = 1
  override def toJob = new TsSystemStatusBundleJob(this)
}

class TsSystemStatusBundleJob(opts: TsSystemStatusBundleOptions) extends BaseCoreJob(opts: TsSystemStatusBundleOptions){

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("ts_bundle_system_status")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    resultsWriter.writeLineStdout(s"TechSupport System Status Bundle Opts $opts")

    val outputTgz = job.path.resolve(TechSupportConstants.DEFAULT_TS_BUNDLE_TGZ)
    val outputDs = job.path.resolve("datastore.json")

    val manifestPath = job.path.resolve(TechSupportConstants.DEFAULT_TS_MANIFEST_JSON)

    FileUtils.writeStringToFile(manifestPath.toFile, opts.manifest.toJson.prettyPrint)


    // Should clean this up. There's inconsistencies where the the SL Root is used
    // and where smrt-link-system/userdata is used. I believe we only need userdata
    val smrtLinkUserData = opts.smrtLinkSystemRoot.resolve("userdata")

    TechSupportUtils.writeSmrtLinkSystemStatusTgz(opts.manifest.smrtLinkSystemId, smrtLinkUserData, outputTgz, opts.manifest.user,
      opts.manifest.smrtLinkSystemVersion, opts.manifest.dnsName)

    val totalSize = outputTgz.toFile.length()
    val totalSizeMB = totalSize / 1024.0 / 1024.0
    resultsWriter.writeLineStdout(s"Total file size $totalSizeMB MB")

    resultsWriter.writeLineStdout(s"Wrote TGZ to ${outputTgz.toAbsolutePath}")

    val createdAt = JodaDateTime.now()

    // Output DataStore Files.

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logDsFile = toMasterDataStoreFile(logPath, s"Job Master log of ${jobTypeId.id}")

    // Write the manifest for adding in debugging and to avoid having to unzip the tgz

    val manifestDs = DataStoreFile(UUID.randomUUID(), "ts-manifest-0", FileTypes.JSON.fileTypeId, manifestPath.toFile.length(),
      createdAt, createdAt, manifestPath.toAbsolutePath.toString, false, "TS System Status Manifest",
      "Tech Support System Status Manifest")

    val dsFile = DataStoreFile(UUID.randomUUID(), "ts-bundle-job-0", FileTypes.TS_TGZ.fileTypeId,
      outputTgz.toFile.length(), createdAt, createdAt, outputTgz.toAbsolutePath.toString, isChunked = false,
      "TechSupport System Status", s"Tech Support System Status TGZ bundle for SL System Root ${opts.smrtLinkSystemRoot}")

    val ds = PacBioDataStore(createdAt, createdAt, "0.2.0", Seq(dsFile, logDsFile, manifestDs))
    FileUtils.writeStringToFile(outputDs.toFile, ds.toJson.prettyPrint)

    resultsWriter.writeLineStdout(s"Successfully create TS TGZ bundle ${opts.manifest.id}")
    Right(ds)
  }
}
