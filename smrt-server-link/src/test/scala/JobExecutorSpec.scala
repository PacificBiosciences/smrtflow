import akka.actor.{ActorRefFactory, ActorSystem}
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.models.{UserRecord, UserResponse}
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.services.jobtypes.MockPbsmrtpipeJobTypeProvider
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{JobRunnerProvider, JobManagerServiceProvider}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import spray.http.OAuth2BearerToken
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration


class JobExecutorSpec extends Specification
with Specs2RouteTest
with SetupMockData
with JobServiceConstants {

  sequential

  import SmrtLinkJsonProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  val READ_USER_LOGIN = "reader"
  val WRITE_USER_1_LOGIN = "root"
  val WRITE_USER_2_LOGIN = "writer2"
  val INVALID_JWT = "invalid.jwt"
  val READ_CREDENTIALS = OAuth2BearerToken(READ_USER_LOGIN)
  val WRITE_CREDENTIALS_1 = OAuth2BearerToken(WRITE_USER_1_LOGIN)
  val WRITE_CREDENTIALS_2 = OAuth2BearerToken(WRITE_USER_2_LOGIN)
  val INVALID_CREDENTIALS = OAuth2BearerToken(INVALID_JWT)

  object TestProviders extends
  ServiceComposer with
  JobManagerServiceProvider with
  MockPbsmrtpipeJobTypeProvider with
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

  TestProviders.userDao().createUser(READ_USER_LOGIN, UserRecord("pass"))
  TestProviders.userDao().createUser(WRITE_USER_1_LOGIN, UserRecord("pass"))
  TestProviders.userDao().createUser(WRITE_USER_2_LOGIN, UserRecord("pass"))

  override val dao: JobsDao = TestProviders.jobsDao()
  override val dal: Dal = dao.dal
  val totalRoutes = TestProviders.jobManagerService().prefixedRoutes
  val dbURI = TestProviders.dbURI

  def toJobType(x: String) = s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$x"
  def toJobTypeById(x: String, i: Int) = s"${toJobType(x)}/$i"
  def toJobTypeByIdWithRest(x: String, i: Int, rest: String) = s"${toJobTypeById(x, i)}/$rest"

  val mockOpts = PbSmrtPipeServiceOptions(
    "My-job-name",
    "pbsmrtpipe.pipelines.mock_dev01",
    Seq(BoundServiceEntryPoint("e_01", "PacBio.DataSet.SubreadSet", 1)),
    Nil,
    Nil)

  val url = toJobType("mock-pbsmrtpipe")

  def dbSetup() = {
    println("Running db setup")
    logger.info(s"Running tests from db-uri ${dbURI()}")
    runSetup(dao)
    println(s"completed setting up database ${dal.dbURI}.")
  }

  textFragment("creating database tables")
  step(dbSetup())

  "Job Execution Service list" should {

    "return status" in {
      Get(s"/$ROOT_SERVICE_PREFIX/job-manager/status") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "execute job" in {
      val url = toJobType("mock-pbsmrtpipe")
      Post(url, mockOpts) ~> totalRoutes ~> check {
        val msg = responseAs[EngineJob]
        logger.info(s"Response to $url -> $msg")
        status.isSuccess must beTrue
      }
    }

    "access job by id" in {
      Get(toJobTypeById("mock-pbsmrtpipe", 1)) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job datastore" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "datastore")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job reports" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "reports")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job events by job id" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "events")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }

    //    "Create a Job Event" in {
    //      val r = JobEventRecord("RUNNING", "Task x is running")
    //      Post(toJobTypeByIdWithRest("mock-pbsmrtpipe", 2, "events"), r) ~> totalRoutes ~> check {
    //        status.isSuccess must beTrue
    //      }
    //    }
  }
}
