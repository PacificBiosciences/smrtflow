package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobResultsWriter,
  CoreJobUtils
}
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

/**
  * Created by mkocher on 8/17/17.
  */
case class SimpleJobOptions(
    n: Int,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID),
    submit: Option[Boolean] = Some(JobConstants.SUBMIT_DEFAULT_CORE_JOB),
    tags: Option[String] = None)
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.SIMPLE
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new SimpleJob(this)
}

trait SimpleDevJob extends CoreJobUtils {
  protected def runDevJob(job: JobResourceBase,
                          resultsWriter: JobResultsWriter,
                          n: Int): PacBioDataStore = {
    // Just to have Data to import back into the system
    val resources = setupJobResourcesAndCreateDirs(job.path)
    val dsFiles = toMockDataStoreFiles(job.path)
    val ds = toDatastore(resources, dsFiles)
    writeDataStore(ds, resources.datastoreJson)
    val report =
      ReportUtils.toMockTaskReport("smrtflow_dev_simple", "smrtflow Report")
    ReportUtils.writeReport(report, resources.jobReportJson)
    resultsWriter.write(s"Simple job ${n}")
    ds
  }
}

class SimpleJob(opts: SimpleJobOptions)
    extends ServiceCoreJob(opts)
    with SimpleDevJob {
  type Out = PacBioDataStore
  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    Right(runDevJob(resources, resultsWriter, opts.n))
  }
}
