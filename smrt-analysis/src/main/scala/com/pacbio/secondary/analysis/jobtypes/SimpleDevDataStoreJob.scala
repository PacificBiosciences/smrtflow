package com.pacbio.secondary.analysis.jobtypes

import com.pacbio.secondary.analysis.jobs.{JobResultWriter, BaseCoreJob, BaseJobOptions, CoreJobModel}
import com.pacbio.secondary.analysis.jobs.JobModels._

case class SimpleDevDataStoreJobOptions(maxMockFiles: Int) extends BaseJobOptions {
  def toJob = new SimpleDevDataStoreJob(this)
}

/**
 * Simple Dev Job for testing datastore importing
 * Created by mkocher on 4/28/15.
 */
class SimpleDevDataStoreJob(opts: SimpleDevDataStoreJobOptions)
  extends BaseCoreJob(opts: SimpleDevDataStoreJobOptions) with MockJobUtils {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("dev_simple_datastore")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toMasterDataStoreFile(logPath)
    // Just to have Data to import back into the system
    val resources = setupJobResourcesAndCreateDirs(job.path)
    val dsFiles = toMockDataStoreFiles(job.path) ++ Seq(logFile)
    val ds = toDatastore(resources, dsFiles)
    writeDataStore(ds, resources.datastoreJson)
    // Just to make the jobOptions take longer
    Thread.sleep(500)
    Right(ds)
  }
}
