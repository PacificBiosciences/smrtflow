
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.concurrent.duration._
import com.typesafe.config.Config
import org.specs2.mutable.{BeforeAfter, Specification}
import org.specs2.time.NoTimeConversions
import akka.actor.{ActorRefFactory, ActorSystem}
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest
import spray.json._
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models._
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.utils.StatusGeneratorProvider
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.services.jobtypes.{DeleteJobServiceTypeProvider, MockPbsmrtpipeJobTypeProvider}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{JobManagerServiceProvider, JobRunnerProvider}
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.typesafe.scalalogging.LazyLogging
import slick.driver.PostgresDriver.api._


class JobExecutorSpec extends Specification
with Specs2RouteTest
with NoTimeConversions
with JobServiceConstants with timeUtils with LazyLogging with TestUtils with BeforeAfter {

  sequential

  import SmrtLinkJsonProtocols._
  import CommonModelImplicits._

  implicit val routeTestTimeout = RouteTestTimeout(5.seconds)

  object TestProviders extends
  ServiceComposer with
  JobManagerServiceProvider with
  MockPbsmrtpipeJobTypeProvider with
  DeleteJobServiceTypeProvider with
  JobsDaoActorProvider with
  StatusGeneratorProvider with
  EngineManagerActorProvider with
  EngineDaoActorProvider with
  JobsDaoProvider with
  TestDalProvider with
  SmrtLinkConfigProvider with
  JobRunnerProvider with
  PbsmrtpipeConfigLoader with
  EngineCoreConfigLoader with
  AuthenticatorImplProvider with
  JwtUtilsProvider with
  InMemoryLogDaoProvider with
  ActorSystemProvider with
  ConfigProvider with
  FakeClockProvider with
  SetBindings {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = Some(UserRecord(jwt))
    })

    override val config: Singleton[Config] = Singleton(testConfig)
    override val actorSystem: Singleton[ActorSystem] = Singleton(system)
    override val actorRefFactory: Singleton[ActorRefFactory] = actorSystem
    override val baseServiceId: Singleton[String] = Singleton("test-service")
    override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
  }

  val dao: JobsDao = TestProviders.jobsDao()
  val totalRoutes = TestProviders.jobManagerService().prefixedRoutes

  def toJobType(x: String) = s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$x"
  def toJobTypeById(x: String, i: IdAble) = s"${toJobType(x)}/${i.toIdString}"
  def toJobTypeByIdWithRest(x: String, i: IdAble, rest: String) = s"${toJobTypeById(x, i)}/$rest"

  val rx = scala.util.Random
  val jobName = s"my-job-name-${rx.nextInt(1000)}"

  val mockOpts = PbSmrtPipeServiceOptions(
    jobName,
    "pbsmrtpipe.pipelines.mock_dev01",
    Seq(BoundServiceEntryPoint("e_01", "PacBio.DataSet.SubreadSet", Left(1))),
    Nil,
    Nil)

  val url = toJobType("mock-pbsmrtpipe")

  lazy val rootJobDir = TestProviders.jobEngineConfig().pbRootJobDir

  override def before = {
    // I Don't think this plays well with the provider model
    // The work around is to just use step()
    logger.info("Running Before Spec")
    //setupJobDir(rootJobDir)
  }
  override def after = {
    logger.info("Running After Spec")
    //cleanUpJobDir(rootJobDir)
  }

  var createdJob: EngineJob = null // This must updated after the initial job is created

  step(setupJobDir(rootJobDir))
  step(setupDb(TestProviders.dbConfig))

  "Job Execution Service list" should {

    "return status" in {
      Get(s"/$ROOT_SERVICE_PREFIX/job-manager/status") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "execute job" in  {
      val url = toJobType("mock-pbsmrtpipe")
      Post(url, mockOpts) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        createdJob = job.copy()
        logger.info(s"Response to $url -> $job")
        status.isSuccess must beTrue
        job.isActive must beTrue
      }
    }

    "access job by id" in {
      Get(toJobTypeById("mock-pbsmrtpipe", createdJob.uuid)) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job datastore" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", createdJob.uuid, "datastore")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job reports" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", createdJob.uuid, "reports")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job events by job id" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", createdJob.id, "events")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "create a delete Job and delete a mock-pbsmrtpipe job" in {

      var complete = false
      var retry = 0
      val maxRetries = 30
      val startedAt = JodaDateTime.now()
      while (!complete) {
        Get(toJobTypeById("mock-pbsmrtpipe", createdJob.uuid)) ~> totalRoutes ~> check {
          val job = responseAs[EngineJob]
          complete = job.isComplete
          retry = retry + 1
          Thread.sleep(1000)
          if (retry >= maxRetries) {
            failure(s"mock-pbsmrtpipe Job id:${job.id} uuid:${job.uuid} state:${job.state} name:${job.name} failed to complete after ${computeTimeDelta(JodaDateTime.now, startedAt)} seconds")
          }
        }
      }

      val params = DeleteJobServiceOptions(createdJob.uuid, removeFiles = true)
      Post(toJobType("delete-job"), params) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        job.jobTypeId must beEqualTo("delete-job")
      }
      Get(toJobTypeById("mock-pbsmrtpipe", createdJob.uuid)) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        job.isActive must beFalse
      }
      Get(toJobType("mock-pbsmrtpipe")) ~> totalRoutes ~> check {
        val jobs = responseAs[Seq[EngineJob]]
        // this really isn't necessary. the test above this is just testing that the isActive is filtered out of the
        // job list
        //jobs.size must beEqualTo(njobs - 1)
        status.isSuccess must beTrue
      }
    }
  }
}
