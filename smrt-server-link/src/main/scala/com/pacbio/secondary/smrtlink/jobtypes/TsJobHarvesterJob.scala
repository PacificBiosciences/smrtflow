package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.Path

import spray.json._
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.actors.{
  JobsDao,
  SmrtLinkEveMetricsProcessor
}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJobUtils
import com.pacbio.secondary.smrtlink.analysis.techsupport.TechSupportConstants

import org.apache.commons.io.FileUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobProtocols._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class TsJobHarvesterJobOptions(
    user: String,
    comment: String,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID),
    submit: Option[Boolean] = Some(JobConstants.SUBMIT_DEFAULT_CORE_JOB))
    extends ServiceJobOptions
    with TsJobValidationUtils {

  override def jobTypeId = JobTypeIds.TS_JOB_HARVESTER_JOB
  override def toJob(): ServiceCoreJob = new TsJobHarvesterJob(this)

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = None

}

class TsJobHarvesterJob(opts: TsJobHarvesterJobOptions)
    extends ServiceCoreJob(opts)
    with CoreJobUtils
    with TsTgzUploadUtils
    with TsJobValidationUtils
    with SmrtLinkEveMetricsProcessor {

  type Out = PacBioDataStore

  private def toDataStore(outputTgz: Path,
                          stdout: DataStoreFile,
                          resources: JobResourceBase): PacBioDataStore = {

    val tsTgzFile = DataStoreFile.fromFile("ts-bundle-job-0",
                                           FileTypes.TS_TGZ.fileTypeId,
                                           outputTgz,
                                           s"TechSupport Job Harvester")

    val datastore = PacBioDataStore.fromFiles(Seq(stdout, tsTgzFile))
    val outputDs = resources.path.resolve(JobConstants.OUTPUT_DATASTORE_JSON)
    FileUtils.writeStringToFile(outputDs.toFile, datastore.toJson.prettyPrint)

    datastore
  }

  private def harvestAndGenerateDataStore(
      stdoutDsFile: DataStoreFile,
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Future[(PacBioDataStore, Path)] = {
    for {
      outputTgz <- Future.successful(
        resources.path.resolve(TechSupportConstants.DEFAULT_TS_HARVEST_TGZ))
      jobs <- harvestAnalysisJobsToTechSupportTgz(config.smrtLinkSystemId,
                                                  opts.user,
                                                  config.smrtLinkVersion,
                                                  Some(config.host),
                                                  dao,
                                                  5,
                                                  outputTgz)
      _ <- Future.successful(
        resultsWriter.writeLine(
          s"Harvested ${jobs.length} SMRT Link Analysis Jobs"))
      dataStore <- Future.successful(
        toDataStore(outputTgz, stdoutDsFile, resources))

    } yield (dataStore, outputTgz)
  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val startedAt = JodaDateTime.now()

    val fx = for {
      stdoutDsFile <- addStdOutLogToDataStore(resources, dao, opts.projectId)
      eveUrl <- validateEveUrl(config.externalEveUrl)
      (dataStore, tgzPath) <- harvestAndGenerateDataStore(stdoutDsFile,
                                                          resources,
                                                          resultsWriter,
                                                          dao,
                                                          config)
      _ <- upload(eveUrl, config.eveApiSecret, tgzPath, resultsWriter)
    } yield dataStore

    convertTry(runAndBlock(fx, DEFAULT_MAX_UPLOAD_TIME),
               resultsWriter,
               startedAt,
               resources.jobId)
  }

}
