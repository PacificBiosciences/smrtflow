package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.secondary.smrtlink.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.UserRecord
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJob
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MovieMetadataToHdfSubreadOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RsConvertMovieToDataSetServiceType(dbActor: ActorRef,
                                         authenticator: Authenticator,
                                         smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.CONVERT_RS_MOVIE.id
    override val description = "Import RS metadata.xml and create an HdfSubread DataSet XML file"
  } with JobTypeService[MovieMetadataToHdfSubreadOptions](dbActor, authenticator) with LazyLogging {

  override def createJob(sopts: MovieMetadataToHdfSubreadOptions, user: Option[UserRecord]): Future[CreateJobType] = Future {
    val uuid = UUID.randomUUID()
    val coreJob = CoreJob(uuid, sopts)
    val jsonSettings = sopts.toJson.toString()
    CreateJobType(
      uuid,
      s"Job $endpoint", s"RS movie convert to HdfSubread XML ",
      endpoint,
      coreJob,
      None,
      jsonSettings,
      user.map(_.userId),
      user.flatMap(_.userEmail),
      smrtLinkVersion)
  }
}

trait RsConvertMovieToDataSetServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider
    with SmrtLinkConfigProvider =>

  val rsConvertMovieToDataSetServiceType: Singleton[RsConvertMovieToDataSetServiceType] =
    Singleton(() => new RsConvertMovieToDataSetServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
