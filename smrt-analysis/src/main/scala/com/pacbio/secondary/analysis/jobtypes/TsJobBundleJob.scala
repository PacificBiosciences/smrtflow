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


    TechSupportUtils.writeJobBundleTgz(opts.jobRoot, opts.manifest, outputTgz)

    val totalSizeMB = outputTgz.toFile.length / 1024.0 / 1024.0
    resultsWriter.writeLineStdout(s"Total file size $totalSizeMB MB")

    // Create DataStore
    val createdAt = JodaDateTime.now()
    val name = s"TS Job ${opts.manifest.jobTypeId} id:${opts.manifest.jobId} Bundle "
    val description = s"TechSupport Bundle for Job type:${opts.manifest.jobTypeId} id: ${opts.manifest.jobTypeId}"

    val dsFile = DataStoreFile(UUID.randomUUID(), "ts-bundle-job-0", FileTypes.TS_TGZ.fileTypeId,
      outputTgz.toFile.length(), createdAt, createdAt, outputTgz.toAbsolutePath.toString, isChunked = false, name, description)

    // This should add the stdout as the "log"
    val ds = PacBioDataStore(createdAt, createdAt, "0.2.0", Seq(dsFile))
    FileUtils.writeStringToFile(outputDs.toFile, ds.toJson.prettyPrint)

    resultsWriter.writeLineStdout(s"Successfully create TS TGZ bundle ${opts.manifest.id}")
    Right(ds)
  }
}
