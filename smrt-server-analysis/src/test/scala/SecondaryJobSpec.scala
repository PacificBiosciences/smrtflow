import akka.actor.{ActorSystem, ActorRefFactory}
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.database.TestDatabaseProvider
import com.pacbio.common.dependency.{InitializationComposer, ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.services.{StatusGeneratorProvider, ServiceComposer}
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.services.{JobRunnerProvider, JobManagerServiceProvider}
import com.pacbio.secondary.smrtlink.services.jobtypes.MockPbsmrtpipeJobTypeProvider
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration


class SecondaryJobSpec extends Specification
with Specs2RouteTest
with SetupMockData
with JobServiceConstants {

  import SmrtLinkJsonProtocols._

  sequential

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  val INVALID_JWT = "invalid.jwt"

  object TestProviders extends
  ServiceComposer with
  InitializationComposer with
  JobManagerServiceProvider with
  StatusGeneratorProvider with
  MockPbsmrtpipeJobTypeProvider with
  JobsDaoActorProvider with
  EngineManagerActorProvider with
  EngineDaoActorProvider with
  JobsDaoProvider with
  SmrtLinkConfigProvider with
  TestDatabaseProvider with
  JobRunnerProvider with
  PbsmrtpipeConfigLoader with
  EngineCoreConfigLoader with
  InMemoryUserDaoProvider with
  AuthenticatorImplProvider with
  JwtUtilsProvider with
  InMemoryLogDaoProvider with
  ActorSystemProvider with
  ConfigProvider with
  FakeClockProvider with
  MetricsProvider with
  InMemoryHealthDaoProvider with
  SetBindings {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def getJwt(user: ApiUser): String = user.login
      override def validate(jwt: String): Option[String] = if (jwt == INVALID_JWT) None else Some(jwt)
    })

    override val config: Singleton[Config] = Singleton(testConfig)
    override val actorSystem: Singleton[ActorSystem] = Singleton(system)
    override val actorRefFactory: Singleton[ActorRefFactory] = actorSystem
    override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
    override val baseServiceId: Singleton[String] = Singleton("test_package")
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  val totalRoutes = TestProviders.jobManagerService().prefixedRoutes
  val dbURI = TestProviders.getFullURI(TestProviders.dbURI())

  def dbSetup() = {
    println("Running db setup")
    logger.info(s"Running tests from db-uri $dbURI")
    runSetup(dbURI)
    println(s"completed setting up database $dbURI")
  }

  textFragment("creating database tables")
  step(dbSetup())

  def toJobType(x: String) = s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$x"
  def toJobTypeById(x: String, i: Int) = s"${toJobType(x)}/$i"
  def toJobTypeByIdWithRest(x: String, i: Int, rest: String) = s"${toJobTypeById(x, i)}/$rest"

  "Service list" should {
    var id = -1
    "Secondary analysis access jobs" in {
      Get(toJobType("mock-pbsmrtpipe")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[Seq[EngineJob]]
        resp.isEmpty === false
        id = resp.head.id
        ok
      }
    }
    "Secondary analysis access job by id" in {
      Get(toJobTypeById("mock-pbsmrtpipe", id)) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis access job datastore" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", id, "datastore")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis access job reports" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", id, "reports")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis access job events by job id" in {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", id, "events")) ~> totalRoutes ~> check {
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
