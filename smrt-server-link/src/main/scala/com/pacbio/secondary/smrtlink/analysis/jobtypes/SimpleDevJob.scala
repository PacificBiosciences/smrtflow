package com.pacbio.secondary.smrtlink.analysis.jobtypes

import com.pacbio.secondary.smrtlink.analysis.jobs.{
  BaseCoreJob,
  BaseJobOptions,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils

// Simple Dev Job for testing
case class SimpleDevJobOptions(
    a: Int,
    b: Int,
    override val projectId: Int = GENERAL_PROJECT_ID)
    extends BaseJobOptions {
  def toJob = new SimpleDevJob(this)
}

class SimpleDevJob(opts: SimpleDevJobOptions)
    extends BaseCoreJob(opts: SimpleDevJobOptions)
    with MockJobUtils {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeIds.SIMPLE

  def run(job: JobResourceBase,
          resultsWriter: JobResultsWriter): Either[ResultFailed, Out] = {

    // Just to have Data to import back into the system
    val resources = setupJobResourcesAndCreateDirs(job.path)
    val dsFiles = toMockDataStoreFiles(job.path)
    val ds = toDatastore(resources, dsFiles)
    writeDataStore(ds, resources.datastoreJson)
    val report =
      ReportUtils.toMockTaskReport("smrtflow_dev_simple", "smrtflow Report")
    ReportUtils.writeReport(report, resources.jobReportJson)
    val total = opts.a + opts.b
    resultsWriter.write(s"Simple job ${opts.a} + ${opts.b} = $total ")
    Thread.sleep(1000)

    Right(ds)
  }

}
