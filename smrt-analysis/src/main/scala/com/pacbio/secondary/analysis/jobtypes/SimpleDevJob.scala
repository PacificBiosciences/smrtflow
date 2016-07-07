package com.pacbio.secondary.analysis.jobtypes

import com.pacbio.secondary.analysis.jobs.{JobResultWriter, BaseCoreJob, BaseJobOptions}
import com.pacbio.secondary.analysis.jobs.JobModels.{JobTypeId, JobResourceBase, ResultFailed}
import com.pacbio.secondary.analysis.reports.ReportUtils

// Simple Dev Job for testing
case class SimpleDevJobOptions(a: Int, b: Int) extends BaseJobOptions {
  def toJob = new SimpleDevJob(this)
}

class SimpleDevJob(opts: SimpleDevJobOptions)
  extends BaseCoreJob(opts: SimpleDevJobOptions)
  with MockJobUtils {

  type Out = Int
  val jobTypeId = JobTypeId("simple")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    // Just to have Data to import back into the system
    val resources = setupJobResourcesAndCreateDirs(job.path)
    val dsFiles = toMockDataStoreFiles(job.path)
    val ds = toDatastore(resources, dsFiles)
    writeDataStore(ds, resources.datastoreJson)
    val report = ReportUtils.toMockTaskReport("smrtflow_dev_simple", "smrtflow Report")
    ReportUtils.writeReport(report, resources.jobReportJson)
    val total = opts.a + opts.b
    resultsWriter.writeStdout(s"Simple job ${opts.a} + ${opts.b} = $total ")
    Thread.sleep(1000)


    Right(total)
  }

}
