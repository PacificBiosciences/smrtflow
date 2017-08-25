
package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MovieMetadataToHdfSubreadOptions

/**
  * Created by mkocher on 8/17/17.
  */
case class RsConvertMovieToDataSetJobOptions(path: String, name: Option[String], description: Option[String],
                                             projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.CONVERT_RS_MOVIE
  override def validate() = None
  override def toJob() = new RsConvertMovieToDataSetJob(this)
}

class RsConvertMovieToDataSetJob(opts: RsConvertMovieToDataSetJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    // SHIM
    val name = opts.name.getOrElse("RS-to-HdfSubreadSet")
    val oldOpts = MovieMetadataToHdfSubreadOptions(opts.path, name, opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
