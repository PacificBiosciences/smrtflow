package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.JobTypeIds
import com.pacbio.secondary.analysis.jobtypes.SimpleDevJobOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SimpleServiceJobType(dbActor: ActorRef, authenticator: Authenticator)
  extends {
    override val endpoint = JobTypeIds.SIMPLE.id
    override val description = "Simple Job for debugging and development"
  } with JobTypeService[SimpleDevJobOptions](dbActor, authenticator) with LazyLogging {

  override def createJob(opts: SimpleDevJobOptions, user: Option[UserRecord]): Future[CreateJobType] = Future {
    val uuid = UUID.randomUUID()
    val coreJob = CoreJob(uuid, opts)
    val jsonSettings = opts.toJson.toString()
    logger.info(s"Got options $opts")
    CreateJobType(
      uuid,
      s"Job name $endpoint", s"Simple Pipeline ${opts.toString}",
      endpoint,
      coreJob,
      None,
      jsonSettings,
      user.map(_.userId),
      user.flatMap(_.userEmail),
      None)
  }
}

trait SimpleServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider =>

  val simpleServiceJobType: Singleton[SimpleServiceJobType] =
    Singleton(() => new SimpleServiceJobType(jobsDaoActor(), authenticator())).bindToSet(JobTypes)
}
