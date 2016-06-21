import akka.actor.{ActorRefFactory, ActorSystem}
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.utils.StatusGeneratorProvider
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.database.Database
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.services.{JobManagerServiceProvider, JobRunnerProvider}
import com.pacbio.secondary.smrtlink.services.jobtypes.MockPbsmrtpipeJobTypeProvider
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration


class SecondaryJobSpec extends Specification
with Specs2RouteTest
with SetupMockData
with JobServiceConstants {

  sequential

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  val INVALID_JWT = "invalid.jwt"

  object TestProviders extends
  ServiceComposer with
  JobManagerServiceProvider with
  MockPbsmrtpipeJobTypeProvider with
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
  InMemoryUserDaoProvider with
  UserServiceActorRefProvider with
  AuthenticatorImplProvider with
  JwtUtilsProvider with
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
  override val db: Database = dao.db
  val totalRoutes = TestProviders.jobManagerService().prefixedRoutes
  val dbURI = TestProviders.dbURI

  trait daoSetup extends Scope {
    println("Running db setup")
    logger.info(s"Running tests from db-uri ${dbURI()}")
    runSetup(dao)
    println(s"completed setting up database ${db.dbUri}")
  }

  def toJobType(x: String) = s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$x"
  def toJobTypeById(x: String, i: Int) = s"${toJobType(x)}/$i"
  def toJobTypeByIdWithRest(x: String, i: Int, rest: String) = s"${toJobTypeById(x, i)}/$rest"

  "Service list" should {
    "Secondary analysis access job by id" in new daoSetup {
      Get(toJobTypeById("mock-pbsmrtpipe", 1)) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis access job datastore" in new daoSetup {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "datastore")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis access job reports" in new daoSetup {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "reports")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis access job events by job id" in new daoSetup {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "events")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
//    "Create a Job Event" in new daoSetup {
//      val r = JobEventRecord("RUNNING", "Task x is running")
//      Post(toJobTypeByIdWithRest("mock-pbsmrtpipe", 2, "events"), r) ~> totalRoutes ~> check {
//        status.isSuccess must beTrue
//      }
//    }
  }
}
