package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.MovieMetadataToHdfSubreadOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
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
