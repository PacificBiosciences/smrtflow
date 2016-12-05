package com.pacbio.secondary.analysis.jobtypes

import com.pacbio.secondary.analysis.datasets.validators.ValidateHdfSubreadSet
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.{DataSetWriter, DataSetLoader}
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacbio.secondary.analysis.converters.MovieMetadataConverter._
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._

import scalaz.{Success, Failure}

import java.nio.file.{Paths, Path}
import java.util.UUID
import org.joda.time.{DateTime => JodaDateTime}


// Importing Movies -> HdfSubread DataSet
case class MovieMetadataToHdfSubreadOptions(path: String, name: String) extends BaseJobOptions {
  def toJob = new RsMovieToHdfDataSetJob(this)
}

/**
 * Import and Convert a RS-era Movie metadata XML file to -> HdfSubread DataSet
 *
 * Created by mkocher on 5/1/15.
 */
class RsMovieToHdfDataSetJob(opts: MovieMetadataToHdfSubreadOptions) extends BaseCoreJob(opts: MovieMetadataToHdfSubreadOptions)
with MockJobUtils with timeUtils {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("rs_movie_to_hdfsubread")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {
    // Just to have Data to import back into the system
    val startedAt = JodaDateTime.now()

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toMasterDataStoreFile(logPath, "Job Master log of the Import Dataset job")

    val dsPath = job.path.resolve("rs_movie.hdfsubreadset.xml")

    convertMovieOrFofnToHdfSubread(opts.path) match {

      case Right(dataset) =>
        ValidateHdfSubreadSet.validator(dataset) match {
          case Success(ds) =>
            dataset.setName(opts.name)
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
              "RS movie XML converted to PacBio HdfSubreadSet XML")

            val resources = setupJobResourcesAndCreateDirs(job.path)
            val ds = toDatastore(resources, Seq(dsFile, logFile))
            writeDataStore(ds, resources.datastoreJson)
            Right(ds)
          case Failure(errorsNel) =>
            val msg = errorsNel.list.mkString("; ")
            Left(ResultFailed(job.jobId, jobTypeId.toString, s"Failed to convert ${opts.path}. $msg", computeTimeDeltaFromNow(startedAt), AnalysisJobStates.FAILED, host))
        }
      case Left(ex) =>
        Left(ResultFailed(job.jobId, jobTypeId.toString, s"Failed to convert ${opts.path}. ${ex.msg}", computeTimeDeltaFromNow(startedAt), AnalysisJobStates.FAILED, host))
    }

  }
}
