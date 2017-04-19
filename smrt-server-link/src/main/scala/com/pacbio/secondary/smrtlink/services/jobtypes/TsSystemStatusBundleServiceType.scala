package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import org.joda.time.{DateTime => JodaDateTime}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._
import spray.httpx.SprayJsonSupport._
import com.typesafe.scalalogging.LazyLogging


import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{CommonModelImplicits, Constants, UserRecord}
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{BundleTypes, JobTypeIds, TsSystemStatusManifest}
import com.pacbio.secondary.analysis.jobtypes.TsSystemStatusBundleOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor.CreateJobType
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryModels.TsSystemStatusServiceOptions
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider



class TsSystemStatusBundleServiceType(dbActor: ActorRef, authenticator: Authenticator, smrtLinkVersion: Option[String]) extends {
  override val endpoint = JobTypeIds.TS_SYSTEM_STATUS.id
  override val description = "TechSupport Job to create a TGZ bundle of an SMRT Link System Status that can be sent to PacBio for TroubleShooting help"
} with JobTypeService[TsSystemStatusServiceOptions](dbActor, authenticator) with ProjectIdJoiner with LazyLogging {

  import CommonModelImplicits._

  def toManifest(user: String, comment: String, smrtLinkSystemUUID: UUID, dnsName: Option[String]) = {
    TsSystemStatusManifest(UUID.randomUUID(), BundleTypes.JOB, 1, JodaDateTime.now(), smrtLinkSystemUUID,
      dnsName, smrtLinkVersion, user, Some(comment))
  }

  override def createJob(opts: TsSystemStatusServiceOptions, user: Option[UserRecord]): Future[CreateJobType] = {

    val smrtLinkSystemRoot: Option[Path] = Some(Paths.get("/"))
    val errorMessage = "System is not configured with SMRT Link System Root. Can NOT create TS Status Job"

    val jobId = UUID.randomUUID()
    val name = "Tech Support System Status bundle"
    // This isn't great and might create confusion, but re-using the
    // job uuid as the tech support bundle (and event) UUID might be a good idea
    val manifestUUID = UUID.randomUUID()

    // mock out to get dependencies correct. These need to be initialized to all core service job init, which
    // will require all jobs to be initialized using these required system values?
    val smrtLinkSystemUUID = Constants.SERVER_UUID
    val dnsName = "UNKNOWN"

    // If the system isn't configured, we fail to create the Job
    val fx = smrtLinkSystemRoot.map { root =>
      CreateJobType(jobId, name, opts.comment, endpoint, CoreJob(jobId,
        TsSystemStatusBundleOptions(root, toManifest(opts.user, opts.comment, smrtLinkSystemUUID, Some(dnsName)))),
        None,
        opts.toJson.toString(),
        user.map(_.userId),
        smrtLinkVersion)
    }

    fx.map(j => Future.successful(j))
        .getOrElse(Future.failed(throw new UnprocessableEntityError(errorMessage)))
  }
}

trait TsSystemStatusBundleServiceTypeProvider {
  this: JobsDaoActorProvider
      with AuthenticatorProvider
      with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val tsSystemStatusBundleJobServiceType: Singleton[TsSystemStatusBundleServiceType] =
    Singleton(() => new TsSystemStatusBundleServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
