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
    val tempDir = Files.createTempDirectory("ts-manifest")
    val manifestPath = tempDir.resolve("ts-manifest.json")
    FileUtils.writeStringToFile(manifestPath.toFile, opts.manifest.toJson.prettyPrint)

    val outputTgz = job.path.resolve("ts-bundle.tgz")
    val outputDs = job.path.resolve("datastore.json")

    TarGzUtil.createTarGzip(tempDir, outputTgz.toFile)
    resultsWriter.writeLineStdout(s"Wrote TGZ to ${outputTgz.toAbsolutePath}")

    val createdAt = JodaDateTime.now()

    val name = "TechSupport System Status"
    val description = s"Tech Support System Status TGZ bundle for SL System Root ${opts.smrtLinkSystemRoot}"

    val dsFile = DataStoreFile(UUID.randomUUID(), "ts-bundle-job-0", FileTypes.TGZ.fileTypeId,
      outputTgz.toFile.length(), createdAt, createdAt, outputTgz.toAbsolutePath.toString, isChunked = false, name, description)

    val ds = PacBioDataStore(createdAt, createdAt, "0.2.0", Seq(dsFile))
    FileUtils.writeStringToFile(outputDs.toFile, ds.toJson.prettyPrint)

    resultsWriter.writeLineStdout(s"Successfully create TS TGZ bundle ${opts.manifest.id}")
    Right(ds)
  }
}
