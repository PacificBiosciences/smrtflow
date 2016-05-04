import akka.actor.{ActorSystem, ActorRefFactory}
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.analysis.jobtypes.SimpleDevJobOptions
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.{BoundServiceEntryPoint, EngineJobResponse, PbSmrtPipeServiceOptions, ServiceTaskOptionBase}
import com.pacbio.secondary.smrtlink.services.jobtypes.MockPbsmrtpipeJobTypeProvider
import com.pacbio.secondary.smrtlink.services.{JobManagerServiceProvider, JobRunnerProvider}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.pacbio.secondary.smrtserver.services.jobtypes.SimpleServiceJobTypeProvider
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration


class JobExecutorSpec extends Specification
with Specs2RouteTest
with SetupMockData
with JobServiceConstants {

  // TODO(smcclellan): This test succeeds when run on its own, but fails when run with other tests, even though parallelExecution is disabled???
  sequential

  import SecondaryAnalysisJsonProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  val INVALID_JWT = "invalid.jwt"

  object TestProviders extends
  ServiceComposer with
  JobManagerServiceProvider with
  MockPbsmrtpipeJobTypeProvider with
  SimpleServiceJobTypeProvider with
  JobsDaoActorProvider with
  StatusServiceActorRefProvider with
  EngineManagerActorProvider with
  EngineDaoActorProvider with
  JobsDaoProvider with
  TestDalProvider with
  SmrtLinkConfigProvider with
  JobRunnerProvider with
  PbsmrtpipeConfigLoader with
  EngineCoreConfigLoader with
  InMemoryUserDaoProvider with
  UserServiceActorRefProvider with
  AuthenticatorImplProvider with
  JwtUtilsProvider with
  LogServiceActorRefProvider with
  InMemoryLogDaoProvider with
  ActorSystemProvider with
  ConfigProvider with
  FakeClockProvider with
  SetBindings {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def getJwt(user: ApiUser): String = user.login
      override def validate(jwt: String): Option[String] = if (jwt == INVALID_JWT) None else Some(jwt)
    })

    override val config: Singleton[Config] = Singleton(testConfig)
    override val actorSystem: Singleton[ActorSystem] = Singleton(system)
    override val actorRefFactory: Singleton[ActorRefFactory] = actorSystem
    override val baseServiceId: Singleton[String] = Singleton("test-service")
    override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val dal: Dal = dao.dal
  val totalRoutes = TestProviders.jobManagerService().prefixedRoutes
  val dbURI = TestProviders.dbURI

  def toJobType(x: String) = s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$x"

  val mockOpts = {
    val ep = BoundServiceEntryPoint("e_01", "DataSet.Subread.", 1)
    val eps = Seq(ep)
    val taskOptions = Seq[ServiceTaskOptionBase]()
    val workflowOptions = Seq[ServiceTaskOptionBase]()
    PbSmrtPipeServiceOptions(
      "My-job-name",
      "pbsmrtpipe.pipelines.mock_dev01",
      eps,
      taskOptions,
      workflowOptions)
  }

  def dbSetup() = {
    println("Running db setup")
    logger.info(s"Running tests from db-uri ${dbURI()}")
    runSetup(dao)
    println(s"completed setting up database ${dal.dbURI}")
  }

  textFragment("creating database tables")
  step(dbSetup())

  "Job Execution Status" should {
    "job execution status" in {
      Get(s"/$ROOT_SERVICE_PREFIX/job-manager/status") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Sanity 'Example' Job Execution test" in {
      val url = toJobType("mock-pbsmrtpipe")
      Post(url, mockOpts) ~> totalRoutes ~> check {
        //val msg = responseAs[EngineJobResponse]
        val msg = responseAs[String]
        logger.info(s"Response to $url -> $msg")
        status.isSuccess must beTrue
      }
    }
  }

  "Job Simple Example " should {
    "Mock 'simple' job " in {
      val r = SimpleDevJobOptions(5, 6)
      val url = toJobType("simple")
      Post(url, r) ~> totalRoutes ~> check {
        //val msg = responseAs[EngineJobResponse]
        val msg = responseAs[String]
        logger.info(s"Response to $url -> $msg")
        status.isSuccess must beTrue
      }
    }
  }
}
