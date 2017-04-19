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


class TsJobBundleJobServiceType(dbActor: ActorRef, authenticator: Authenticator, smrtLinkVersion: Option[String], smrtLinkSystemId: UUID, dnsName: Option[String]) extends {
  override val endpoint = JobTypeIds.TS_JOB.id
  override val description = "TechSupport Job to create a TGZ bundle of an SMRT Link Job that can be sent to PacBio for TroubleShooting help"
} with JobTypeService[TsJobBundleJobServiceOptions](dbActor, authenticator) with ProjectIdJoiner with LazyLogging {

  import CommonModelImplicits._

  def toTsManifest(manifestUUID: UUID, user: String, comment: String, smrtLinkSystemUUID: UUID, jobId: Int, jobType: String, dnsName: Option[String]): TsJobManifest = {
    TsJobManifest(manifestUUID, BundleTypes.JOB, 1, JodaDateTime.now(), smrtLinkSystemUUID,
      dnsName, smrtLinkVersion, user, Some(comment), endpoint, jobId)
  }

  override def createJob(opts: TsJobBundleJobServiceOptions, user: Option[UserRecord]): Future[CreateJobType] = {

    // This isn't great and might create confusion, but re-using the
    // job uuid as the tech support bundle (and event) UUID might be a good idea
    val jobId = UUID.randomUUID()

    for {
      engineJob <- getJob(opts.jobId)
    } yield CreateJobType(jobId, "Tech Support Job bundle", opts.comment, endpoint,
      CoreJob(jobId,
        TsJobBundleJobOptions(
          Paths.get(engineJob.path),
          toTsManifest(jobId, opts.user, opts.comment, smrtLinkSystemId, engineJob.id, engineJob.jobTypeId, dnsName)
        )),
        None,
        opts.toJson.toString(),
        user.map(_.userId),
        smrtLinkVersion)
  }
}

trait TsJobBundleJobServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val tsJobBundleJobServiceType: Singleton[TsJobBundleJobServiceType] =
    Singleton(() => new TsJobBundleJobServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion(), Constants.SERVER_UUID, dnsName())).bindToSet(JobTypes)

}

