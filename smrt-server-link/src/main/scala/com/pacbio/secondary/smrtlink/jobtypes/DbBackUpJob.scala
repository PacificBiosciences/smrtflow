package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{InvalidJobOptionError, JobResultWriter}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPointRecord
// shim layer
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{DbBackUpJobOptions => OldDbBackUpJobOptions}


case class DbBackUpJobOptions(user: String,
                              comment: String,
                              name: Option[String],
                              description: Option[String],
                              projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.DB_BACKUP
  override def toJob() = new DbBackUpJob(this)

  override def resolveEntryPoints(dao: JobsDao): Seq[EngineJobEntryPointRecord] = Seq.empty[EngineJobEntryPointRecord]

  override def validate(dao: JobsDao, config: SystemJobConfig): Option[InvalidJobOptionError] = {
    config.rootDbBackUp match {
      case Some(_) => None
      case _ => Some(InvalidJobOptionError("Unable to backup database. System is not configured with a DB Backup dir."))
    }
  }


}

class DbBackUpJob(opts: DbBackUpJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig) = {

    // SHIM
    // Assume that validate has already been called.
    val rootBackUp = config.rootDbBackUp.get

    val oldOpts = OldDbBackUpJobOptions(
      rootBackUp,
      dbName = config.dbConfig.dbName,
      dbPort = config.dbConfig.port,
      dbUser = config.dbConfig.username,
      dbPassword = config.dbConfig.password)

    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
