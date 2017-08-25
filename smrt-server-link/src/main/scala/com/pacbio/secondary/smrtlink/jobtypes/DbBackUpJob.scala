package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
// shim layer
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{DbBackUpJobOptions => OldDbBackUpJobOptions}


case class DbBackUpJobOptions(user: String,
                              comment: String,
                              name: Option[String],
                              description: Option[String],
                              projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.DB_BACKUP
  override def validate() = None
  override def toJob() = new DbBackUpJob(this)

}

class DbBackUpJob(opts: DbBackUpJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao) = {

    // SHIM
    // These need to be pulled from the SL System config
    val rootBackUp = Paths.get("/tmp")
    val dbName = "test"
    val dbPort = 5439
    val dbUser = "test-x"
    val dbPassword = "test-x"

    val oldOpts = OldDbBackUpJobOptions(rootBackUp, dbName = dbName, dbPort = dbPort, dbUser = dbUser, dbPassword = dbPassword)

    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
