package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.Path
import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{BoundEntryPoint, JobTypeIds, ServiceTaskOptionBase}
import com.pacbio.secondary.analysis.jobtypes.MockPbSmrtPipeJobOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MockPbsmrtpipeJobType(dbActor: ActorRef,
                            authenticator: Authenticator,
                            smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.MOCK_PBSMRTPIPE.id
    override val description = "Mock Pbmsrtpipe Job used for Development purposes"
  } with JobTypeService[PbSmrtPipeServiceOptions](dbActor, authenticator) with LazyLogging {

  override def createJob(ropts: PbSmrtPipeServiceOptions, user: Option[UserRecord]): Future[CreateJobType] = Future {
    val uuid = UUID.randomUUID()
    val entryPoints = ropts.entryPoints.map(x => BoundEntryPoint(x.entryId, "/tmp/file.fasta"))
    val taskOptions = Seq[ServiceTaskOptionBase]()
    val workflowOptions = Seq[ServiceTaskOptionBase]()
    val envPath: Option[Path] = None
    val opts = MockPbSmrtPipeJobOptions(ropts.pipelineId, entryPoints, taskOptions, workflowOptions, envPath, ropts.projectId)
    val coreJob = CoreJob(uuid, opts)
    logger.info(s"Got options $opts")
    val jsonSettings = ropts.toJson.toString()
    CreateJobType(
      uuid,
      ropts.name,
      s"Mock pbsmrtpipe Pipeline ${opts.toString}",
      endpoint,
      coreJob,
      None,
      jsonSettings,
      user.map(_.userId),
      smrtLinkVersion
    )
  }
}

trait MockPbsmrtpipeJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val mockPbsmrtpipeJobType: Singleton[MockPbsmrtpipeJobType] =
    Singleton(() => new MockPbsmrtpipeJobType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
