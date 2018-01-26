package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Paths, Path}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import scalaz.{Success, Failure}

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.converters.MovieMetadataConverter._
import com.pacbio.secondary.smrtlink.analysis.datasets.validators.ValidateHdfSubreadSet
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetWriter,
  DataSetLoader
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

/**
  * Created by mkocher on 8/17/17.
  */
case class RsConvertMovieToDataSetJobOptions(
    path: String,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.CONVERT_RS_MOVIE
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new RsConvertMovieToDataSetJob(this)
}

class RsConvertMovieToDataSetJob(opts: RsConvertMovieToDataSetJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils {
  type Out = PacBioDataStore
  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    val startedAt = JodaDateTime.now()
    val name = opts.name.getOrElse("RS-to-HdfSubreadSet")
    val logPath = resources.path.resolve(JobConstants.JOB_STDOUT)
    val logFile =
      toSmrtLinkJobLog(
        logPath,
        Some(
          s"${JobConstants.DATASTORE_FILE_MASTER_DESC} of the Import Dataset job"))
    val dsPath = resources.path.resolve("rs_movie.hdfsubreadset.xml")
    val datastoreJson = resources.path.resolve("datastore.json")

    convertMovieOrFofnToHdfSubread(opts.path) match {

      case Right(dataset) =>
        ValidateHdfSubreadSet.validator(dataset) match {
          case Success(ds) =>
            dataset.setName(name)
            // Update the name and rewrite the file
            DataSetWriter.writeHdfSubreadSet(dataset, dsPath)
            val sourceId = s"pbscala::${jobTypeId.id}"

            // FIXME. The timestamps are in the wrong format
            val now = JodaDateTime.now()
            val dsFile = DataStoreFile(
              UUID.fromString(dataset.getUniqueId),
              sourceId,
              DataSetMetaTypes.typeToIdString(DataSetMetaTypes.HdfSubread),
              dsPath.toFile.length(),
              now,
              now,
              dsPath.toAbsolutePath.toString,
              isChunked = false,
              "HdfSubreadSet",
              "RS movie XML converted to PacBio HdfSubreadSet XML"
            )

            val endedAt = JodaDateTime.now()
            val ds = PacBioDataStore(startedAt,
                                     endedAt,
                                     "0.1.0",
                                     Seq(dsFile, logFile))
            writeDataStore(ds, datastoreJson)
            Right(ds)
          case Failure(errorsNel) =>
            val msg = errorsNel.list.toList.mkString("; ")
            Left(
              ResultFailed(resources.jobId,
                           opts.jobTypeId.toString,
                           s"Failed to convert ${opts.path}. $msg",
                           computeTimeDeltaFromNow(startedAt),
                           AnalysisJobStates.FAILED,
                           host))
        }
      case Left(ex) =>
        Left(
          ResultFailed(resources.jobId,
                       opts.jobTypeId.toString,
                       s"Failed to convert ${opts.path}. ${ex.msg}",
                       computeTimeDeltaFromNow(startedAt),
                       AnalysisJobStates.FAILED,
                       host))
    }
  }
}
