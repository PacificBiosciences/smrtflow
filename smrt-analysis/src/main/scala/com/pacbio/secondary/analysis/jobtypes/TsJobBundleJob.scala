package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.Path
import java.util.UUID

import spray.json._
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.{BaseCoreJob, BaseJobOptions, JobResultWriter}
import com.pacbio.secondary.analysis.jobs.SecondaryJobProtocols._
import com.pacbio.secondary.analysis.techsupport.{TechSupportConstants, TechSupportUtils}
import org.apache.commons.io.FileUtils


case class TsJobBundleJobOptions(jobRoot: Path, manifest: TsJobManifest) extends BaseJobOptions {
  // This is just to adhere to the interface
  val projectId = 1
  override def toJob = new TsJobBundleJob(this)

}

class TsJobBundleJob(opts: TsJobBundleJobOptions) extends BaseCoreJob(opts: TsJobBundleJobOptions) {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("ts_bundle_job")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    resultsWriter.writeLineStdout(s"TechSupport Bundle Opts $opts")

    val outputTgz = job.path.resolve(TechSupportConstants.DEFAULT_TS_BUNDLE_TGZ)
    val outputDs = job.path.resolve("datastore.json")

    val manifestPath = job.path.resolve(TechSupportConstants.DEFAULT_TS_MANIFEST_JSON)

    FileUtils.writeStringToFile(manifestPath.toFile, opts.manifest.toJson.prettyPrint)

    TechSupportUtils.writeJobBundleTgz(opts.jobRoot, opts.manifest, outputTgz)

    val totalSizeMB = outputTgz.toFile.length / 1024.0 / 1024.0
    resultsWriter.writeLineStdout(s"Total file size $totalSizeMB MB")

    // Create DataStore
    val createdAt = JodaDateTime.now()

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logDsFile = toMasterDataStoreFile(logPath, s"Job Master log of ${jobTypeId.id}")

    val manifestDs = DataStoreFile(UUID.randomUUID(), "ts-manifest-0", FileTypes.JSON.fileTypeId, manifestPath.toFile.length(),
      createdAt, createdAt, manifestPath.toAbsolutePath.toString, false, "TS System Status Manifest",
      "Tech Support System Status Manifest")

    val dsFile = DataStoreFile(UUID.randomUUID(), "ts-bundle-job-0", FileTypes.TS_TGZ.fileTypeId,
      outputTgz.toFile.length(), createdAt, createdAt, outputTgz.toAbsolutePath.toString, isChunked = false,
      s"TS Job ${opts.manifest.jobTypeId} id:${opts.manifest.jobId} Bundle",
      s"TechSupport Bundle for Job type:${opts.manifest.jobTypeId} id: ${opts.manifest.jobTypeId}")

    // This should add the stdout as the "log"
    val ds = PacBioDataStore(createdAt, createdAt, "0.2.0", Seq(dsFile, logDsFile, manifestDs))
    FileUtils.writeStringToFile(outputDs.toFile, ds.toJson.prettyPrint)

    resultsWriter.writeLineStdout(s"Successfully create TS TGZ bundle ${opts.manifest.id}")
    Right(ds)
  }
}
