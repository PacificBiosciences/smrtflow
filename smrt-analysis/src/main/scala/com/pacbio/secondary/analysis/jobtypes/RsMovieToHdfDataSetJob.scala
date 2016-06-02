package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.{Paths, Path}
import java.util.UUID
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.{DataSetWriter, DataSetLoader}
import com.pacbio.secondary.analysis.tools.timeUtils
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.analysis.converters.MovieMetadataConverter._
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._

import scala.util.{Success, Try, Failure}


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

    val dsPath = job.path.resolve("hdfsubread.dataset.xml")

    convertMovieMetaDataToSubread(Paths.get(opts.path)) match {

      case Right(dataset) =>
        dataset.setName(opts.name)
        // Update the name and rewrite the file
        DataSetWriter.writeHdfSubreadSet(dataset, dsPath)
        val sourceId = s"pbscala::${jobTypeId.id}"

        // FIXME. The timestamps are in the wrong format
        val now = JodaDateTime.now()
        val dsFile = DataStoreFile(
          UUID.fromString(dataset.getUniqueId),
          sourceId,
          DataSetMetaTypes.typeToIdString(DataSetMetaTypes.Reference),
          dsPath.toFile.length(),
          now,
          now,
          dsPath.toAbsolutePath.toString,
          isChunked = false,
          "HdfSubreadSet",
          "RS movie XML converted to PacBio HdfSubreadSet XML")

        val resources = setupJobResourcesAndCreateDirs(job.path)
        val ds = toDatastore(resources, Seq(dsFile))
        writeDataStore(ds, resources.datastoreJson)
        Right(ds)
      case Left(ex) =>
        Left(ResultFailed(job.jobId, jobTypeId.toString, s"Failed to convert ${opts.path}. ${ex.msg}", computeTimeDeltaFromNow(startedAt), AnalysisJobStates.FAILED, host))
    }

  }
}
