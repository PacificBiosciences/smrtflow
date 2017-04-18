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
import org.apache.commons.io.FileUtils


case class TsJobBundleJobOptions(jobRoot: Path, manifest: TsJobManifest) extends BaseJobOptions {
  // This is just to adhere to the interface
  val projectId = 1
  override def toJob = new TsJobBundleJob(this)

}

class TsJobBundleJob(opts: TsJobBundleJobOptions) extends BaseCoreJob(opts: TsJobBundleJobOptions) {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("ts_bundle_job")

  def writeTo(jx: String, path: Path):Path = {
    FileUtils.writeStringToFile(path.toFile, jx)
    path
  }

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    resultsWriter.writeLineStdout(s"TechSupport Bundle Opts $opts")
    val tempDir = Files.createTempDirectory("ts-manifest")
    val manifestPath = tempDir.resolve("ts-manifest.json")
    writeTo(opts.manifest.toJson.prettyPrint, manifestPath)

    val outputTgz = job.path.resolve("ts-bundle.tgz")
    val outputDs = job.path.resolve("datastore.json")

    TarGzUtil.createTarGzip(tempDir, outputTgz.toFile)
    resultsWriter.writeLineStdout(s"Wrote TGZ to ${outputTgz.toAbsolutePath}")

    val createdAt = JodaDateTime.now()
    val name = s"TS Job ${opts.manifest.jobTypeId} id:${opts.manifest.jobId} Bundle "
    val description = s"TechSupport Bundle for Job type:${opts.manifest.jobTypeId} id: ${opts.manifest.jobTypeId}"

    val dsFile = DataStoreFile(UUID.randomUUID(), "ts-bundle-job-0", FileTypes.TGZ.fileTypeId,
      outputTgz.toFile.length(), createdAt, createdAt, outputTgz.toAbsolutePath.toString, isChunked = false, name, description)

    // This should add the stdout as the "log"
    val ds = PacBioDataStore(createdAt, createdAt, "0.2.0", Seq(dsFile))
    writeTo(ds.toJson.prettyPrint, outputDs)

    resultsWriter.writeLineStdout(s"Successfully create TS TGZ bundle ${opts.manifest.id}")
    Right(ds)
  }
}
