import java.nio.file.Paths

import akka.actor.{ActorRefFactory, ActorSystem}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.auth._
import com.pacbio.secondary.smrtlink.dependency.{ConfigProvider, SetBindings, Singleton}
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.services.utils.StatusGeneratorProvider
import com.pacbio.secondary.smrtlink.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.ServiceTaskOptionBase
import com.pacbio.secondary.smrtlink.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.{BoundServiceEntryPoint, PbSmrtPipeServiceOptions, UserRecord}
import com.pacbio.secondary.smrtlink.services.{JobsServiceProvider, ServiceComposer}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.typesafe.config.Config
import org.specs2.mutable.Specification
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration
import slick.driver.PostgresDriver.api._

abstract class SmrtLinkAnalysisJobExecutorSpecBase extends Specification
with Specs2RouteTest
with SetupMockData
with JobServiceConstants with TestUtils{

  sequential

  import CommonModelImplicits._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  val INVALID_JWT = "invalid.jwt"

  object TestProviders extends
  ServiceComposer with
  JobsServiceProvider with
  StatusGeneratorProvider with
  EventManagerActorProvider with
  JobsDaoProvider with
  SmrtLinkTestDalProvider with
  SmrtLinkConfigProvider with
  PbsmrtpipeConfigLoader with
  EngineCoreConfigLoader with
  AuthenticatorImplProvider with
  JwtUtilsProvider with
  ActorSystemProvider with
  ConfigProvider with
  FakeClockProvider with
  SetBindings {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = if (jwt == INVALID_JWT) None else Some(UserRecord(jwt))
    })

    override val config: Singleton[Config] = Singleton(testConfig)
    override val actorSystem: Singleton[ActorSystem] = Singleton(system)
    override val actorRefFactory: Singleton[ActorRefFactory] = actorSystem
    override val baseServiceId: Singleton[String] = Singleton("test-service")
    override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db = dao.db
  val totalRoutes = TestProviders.newJobService().prefixedRoutes

  def toJobType(x: String) = s"/$ROOT_SERVICE_PREFIX/job-manager/jobs/$x"

  val mockOpts = {
    val ep = BoundServiceEntryPoint("e_01", "DataSet.DataSet.SubreadSet", 1)
    val eps = Seq(ep)
    val taskOptions = Seq[ServiceTaskOptionBase]()
    val workflowOptions = Seq[ServiceTaskOptionBase]()
    PbSmrtPipeServiceOptions(
      "My-smrt-server-analysis-job-name",
      "pbsmrtpipe.pipelines.mock_dev01",
      eps,
      taskOptions,
      workflowOptions)
  }

  lazy val rootJobDir = TestProviders.jobEngineConfig().pbRootJobDir

  step(setupJobDir(rootJobDir))
  step(setupDb(TestProviders.dbConfig))
  step(runInsertAllMockData(dao))

}
