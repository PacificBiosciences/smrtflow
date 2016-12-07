
import java.util.UUID

import scala.concurrent.duration._
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import akka.actor.{ActorRefFactory, ActorSystem}
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest
import spray.json._
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.models._
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.utils.StatusGeneratorProvider
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.database.Database
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.services.jobtypes.{DeleteJobServiceTypeProvider, MockPbsmrtpipeJobTypeProvider}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{JobManagerServiceProvider, JobRunnerProvider}
import com.pacbio.secondary.smrtlink.tools.SetupMockData


class JobExecutorSpec extends Specification
with Specs2RouteTest
with SetupMockData
with NoTimeConversions
with JobServiceConstants with timeUtils{

  sequential

  import SmrtLinkJsonProtocols._

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

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.jobManagerService().prefixedRoutes
  val dbURI = TestProviders.dbURI()

  def toJobType(x: String) = s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$x"
  def toJobTypeById(x: String, i: Int) = s"${toJobType(x)}/$i"
  def toJobTypeByIdWithRest(x: String, i: Int, rest: String) = s"${toJobTypeById(x, i)}/$rest"

  val mockOpts = PbSmrtPipeServiceOptions(
    "My-job-name",
    "pbsmrtpipe.pipelines.mock_dev01",
    Seq(BoundServiceEntryPoint("e_01", "PacBio.DataSet.SubreadSet", Left(1))),
    Nil,
    Nil)

  val url = toJobType("mock-pbsmrtpipe")

  trait daoSetup extends Scope {
    println("Running db setup")
    logger.info(s"Running tests from db-uri $dbURI")
    runSetup(dao)
    println(s"completed setting up database $dbURI.")
  }

  "Job Execution Service list" should {

    "return status" in new daoSetup {
      Get(s"/$ROOT_SERVICE_PREFIX/job-manager/status") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "execute job" in new daoSetup {
      val url = toJobType("mock-pbsmrtpipe")
      Post(url, mockOpts) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        logger.info(s"Response to $url -> $job")
        status.isSuccess must beTrue
        job.isActive must beTrue
      }
    }

    "access job by id" in new daoSetup {
      Get(toJobTypeById("mock-pbsmrtpipe", 1)) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job datastore" in new daoSetup {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "datastore")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job reports" in new daoSetup {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "reports")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "access job events by job id" in new daoSetup {
      Get(toJobTypeByIdWithRest("mock-pbsmrtpipe", 1, "events")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "create a delete Job and delete a mock-pbsmrtpipe job" in new daoSetup {
      var njobs = 0
      var uuid = UUID.randomUUID()
      Get(toJobType("mock-pbsmrtpipe")) ~> totalRoutes ~> check {
        val jobs = responseAs[Seq[EngineJob]]
        njobs = jobs.size
        uuid = jobs.head.uuid
        jobs.head.id must beEqualTo(1)
        njobs must beGreaterThan(0)
      }

      var complete = false
      var retry = 0
      val maxRetries = 15
      val startedAt = JodaDateTime.now()
      while (!complete) {
        Get(toJobType("mock-pbsmrtpipe")) ~> totalRoutes ~> check {
          complete = responseAs[Seq[EngineJob]].head.isComplete
          retry = retry + 1
          Thread.sleep(1000)
          if (retry >= maxRetries) {
            failure(s"mock-pbsmrtpipe Job failed to complete after ${computeTimeDelta(JodaDateTime.now, startedAt)} seconds")
          }
        }
      }

      val params = DeleteJobServiceOptions(uuid, removeFiles = true)
      Post(toJobType("delete-job"), params) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        job.jobTypeId must beEqualTo("delete-job")
      }
      Get(toJobTypeById("mock-pbsmrtpipe", 1)) ~> totalRoutes ~> check {
        val job = responseAs[EngineJob]
        job.isActive must beFalse
      }
      Get(toJobType("mock-pbsmrtpipe")) ~> totalRoutes ~> check {
        val jobs = responseAs[Seq[EngineJob]]
        jobs.size must beEqualTo(njobs - 1)
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
