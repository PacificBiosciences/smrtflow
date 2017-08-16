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
import com.pacbio.secondary.smrtlink.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJob
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{BundleTypes, JobTypeIds, TsSystemStatusManifest}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.TsSystemStatusBundleOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor.CreateJobType
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryModels.TsSystemStatusServiceOptions
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.models.UserRecord
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider



class TsSystemStatusBundleServiceType(dbActor: ActorRef, authenticator: Authenticator, smrtLinkVersion: Option[String], smrtLinkSystemId: UUID, dnsName: Option[String], smrtLinkSystemRoot: Option[Path]) extends {
  override val endpoint = JobTypeIds.TS_SYSTEM_STATUS.id
  override val description = "TechSupport Job to create a TGZ bundle of an SMRT Link System Status that can be sent to PacBio for TroubleShooting help"
} with JobTypeService[TsSystemStatusServiceOptions](dbActor, authenticator) with ProjectIdJoiner with LazyLogging {

  import CommonModelImplicits._

  def toTsManifest(manifestUUID: UUID, user: String, comment: String, smrtLinkSystemUUID: UUID, dnsName: Option[String]) = {
    TsSystemStatusManifest(manifestUUID, BundleTypes.SYSTEM_STATUS, 1, JodaDateTime.now(), smrtLinkSystemUUID,
      dnsName, smrtLinkVersion, user, Some(comment))
  }

  override def createJob(opts: TsSystemStatusServiceOptions, user: Option[UserRecord]): Future[CreateJobType] = {

    val errorMessage = "System is not configured with SMRT Link System Root. Can NOT create TS Status Job"

    // This isn't great and might create confusion, but re-using the
    // job uuid as the tech support bundle (and event) UUID might be a good idea
    // Also when the job is created, the UUID of the job can be communicated to the Client (i.e., UI)
    val jobId = UUID.randomUUID()

    val jobOpt = smrtLinkSystemRoot.map { root =>
      CreateJobType(jobId, "Tech Support System Status bundle", opts.comment, endpoint, CoreJob(jobId,
        TsSystemStatusBundleOptions(root, toTsManifest(jobId, opts.user, opts.comment, smrtLinkSystemId, dnsName))),
        None,
        opts.toJson.toString(),
        user.map(_.userId),
        user.flatMap(_.userEmail),
        smrtLinkVersion)
    }

    // If the system isn't configured, we fail to create a Job
    jobOpt.map(j => Future.successful(j))
        .getOrElse(Future.failed(throw new UnprocessableEntityError(errorMessage)))
  }
}

trait TsSystemStatusBundleServiceTypeProvider {
  this: JobsDaoActorProvider
      with AuthenticatorProvider
      with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val tsSystemStatusBundleJobServiceType: Singleton[TsSystemStatusBundleServiceType] =
    Singleton(() => new TsSystemStatusBundleServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion(), serverId(), dnsName(), smrtLinkSystemRoot())).bindToSet(JobTypes)
}
