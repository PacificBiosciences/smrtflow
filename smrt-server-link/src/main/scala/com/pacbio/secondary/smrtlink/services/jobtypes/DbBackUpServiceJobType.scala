package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.{Paths,Path}
import java.util.UUID

import akka.actor.ActorRef

import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.typesafe.scalalogging.LazyLogging

import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.JobTypeIds
import com.pacbio.secondary.analysis.jobtypes.DbBackUpJobOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{JobsDaoActorProvider, SmrtLinkDalProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.database.DatabaseConfig
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.models.SecondaryModels.DbBackUpServiceJobOptions
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider


/**
  * Create a DB backup Job. This requires the system to be configured with a db backup database directory
  */
class DbBackUpServiceJobType(dbActor: ActorRef, authenticator: Authenticator, dbConfig: DatabaseConfig, rootDbBackUpDir: Option[Path]) extends {
  override val endpoint = JobTypeIds.DB_BACKUP.id
  override val description = "SMRT Link DB backup"
} with JobTypeService[DbBackUpServiceJobOptions](dbActor, authenticator) with ProjectIdJoiner with LazyLogging {

  override def createJob(opts: DbBackUpServiceJobOptions, user: Option[UserRecord]): Future[CreateJobType] = {

    val errorMessage = "System is not Configured with database backup dir. Unable to create db backup job"

    val createdBy = Some(opts.user)
    val uuid = UUID.randomUUID()
    val name = s"DB BackUp Job " + createdBy.map(u => s"By User $u").getOrElse("")

    rootDbBackUpDir.map { r =>

      val jobOpts = DbBackUpJobOptions(r,
        dbName = dbConfig.dbName,
        dbUser = dbConfig.username,
        dbPort = dbConfig.port,
        dbPassword = dbConfig.password)

      val coreJob = CoreJob(uuid, jobOpts)
      val cJob = CreateJobType(uuid, name, opts.comment, endpoint, coreJob, None, opts.toJson.toString(), createdBy, None)

      Future.successful(cJob)
    }.getOrElse(Future.failed(throw new UnprocessableEntityError(errorMessage)))
  }
}

trait DbBackUpServiceJobTypeProvider {
  this: JobsDaoActorProvider
      with JobManagerServiceProvider
      with AuthenticatorProvider
      with SmrtLinkConfigProvider
      with SmrtLinkDalProvider =>

  val dbBackUpServiceJobType: Singleton[DbBackUpServiceJobType] =
    Singleton(() => new DbBackUpServiceJobType(jobsDaoActor(), authenticator(), dbConfigSingleton(), rootDataBaseBackUpDir())).bindToSet(JobTypes)

}
