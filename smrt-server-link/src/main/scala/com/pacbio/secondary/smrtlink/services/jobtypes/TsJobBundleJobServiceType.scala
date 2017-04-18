package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{CommonModelImplicits, Constants, UserRecord}
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{BundleTypes, JobTypeIds, TsJobManifest}
import com.pacbio.secondary.analysis.jobtypes.{TsJobBundleJob, TsJobBundleJobOptions}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor.CreateJobType
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryModels.TsJobBundleJobServiceOptions
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._
import spray.httpx.SprayJsonSupport._
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import org.joda.time.{DateTime => JodaDateTime}


class TsJobBundleJobServiceType(dbActor: ActorRef, authenticator: Authenticator, smrtLinkVersion: Option[String]) extends {
  override val endpoint = JobTypeIds.TS_JOB.id
  override val description = "TechSupport Job to create a TGZ bundle of an SMRT Link Job that can be sent to PacBio for TroubleShooting help"
} with JobTypeService[TsJobBundleJobServiceOptions](dbActor, authenticator) with ProjectIdJoiner with LazyLogging {

  import CommonModelImplicits._

  def toManifest(user: String, comment: String, smrtLinkSystemUUID: UUID, jobId: Int, jobType: String, dnsName: Option[String]): TsJobManifest = {
    TsJobManifest(UUID.randomUUID(), BundleTypes.JOB, 1, JodaDateTime.now(), smrtLinkSystemUUID,
      dnsName, smrtLinkVersion, user, Some(comment), endpoint, jobId)
  }

  override def createJob(opts: TsJobBundleJobServiceOptions, user: Option[UserRecord]): Future[CreateJobType] = {

    val jobId = UUID.randomUUID()
    val name = "Tech Support bundle"
    // This isn't great and might create confusion, but re-using the
    // job uuid as the tech support bundle (and event) UUID might be a good idea
    val manifestUUID = UUID.randomUUID()

    // mock out to get dependencies correct. These need to be initialized to all core service job init, which
    // will require all jobs to be initialized using these required system values?
    val smrtLinkSystemUUID = Constants.SERVER_UUID
    val dnsName = "UNKNOWN"

    for {
      engineJob <- getJob(opts.jobId)
    } yield CreateJobType(jobId, name, opts.comment, endpoint, CoreJob(jobId,
      TsJobBundleJobOptions(Paths.get(engineJob.path), toManifest(opts.user, opts.comment, smrtLinkSystemUUID, engineJob.id, engineJob.jobTypeId, Some(dnsName)))),
      None,
      opts.toJson.toString(), user.map(_.userId), smrtLinkVersion)
  }
}

trait TsJobBundleJobServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val tsJobBundleJobServiceType: Singleton[TsJobBundleJobServiceType] =
    Singleton(() => new TsJobBundleJobServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)

}

