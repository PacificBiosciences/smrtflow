package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.Path

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MergeDataSetOptions

/**
  * Created by mkocher on 8/17/17.
  */
case class MergeDataSetJobOptions(datasetType: DataSetMetaTypes.DataSetMetaType,
                                  ids: Seq[Int],
                                  name: Option[String],
                                  description: Option[String],
                                  projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.MERGE_DATASETS
  override def validate() = None
  override def toJob() = new MergeDataSetJob(this)
}

class MergeDataSetJob(opts: MergeDataSetJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {

    val name = opts.name.getOrElse("Merge-DataSet")
    // This have to be resolved from the Dao
    val paths: Seq[String] = Seq.empty[String]
    val oldOpts = MergeDataSetOptions(opts.datasetType.toString, paths, name, opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
