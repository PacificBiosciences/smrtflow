
import scala.concurrent.duration.FiniteDuration

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.httpx.SprayJsonSupport._
import akka.actor.ActorRefFactory
import spray.json._

import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.auth._
import com.pacbio.common.dependency.{SetBindings, Singleton}
import com.pacbio.common.models._
import com.pacbio.common.services.{PacBioServiceErrors, ServiceComposer}
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.database.Database
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.{JobServiceConstants, SmrtLinkConstants}
import com.pacbio.secondary.smrtlink.actors.{JobsDao, JobsDaoActorProvider, JobsDaoProvider, TestDalProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{DataSetServiceProvider, JobRunnerProvider, ProjectServiceProvider, EulaServiceProvider}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import com.pacbio.secondary.smrtlink.app.{SmrtLinkApi, SmrtLinkProviders}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.tools.SetupMockData

class EulaServiceSpec extends Specification
    with Specs2RouteTest
    with SetupMockData
    with PacBioServiceErrors
    with JobServiceConstants
    with SmrtLinkConstants {

  import SmrtLinkJsonProtocols._

  sequential

  val INVALID_JWT = "invalid.jwt"

  object TestProviders extends
      ServiceComposer with
      ProjectServiceProvider with
      SmrtLinkConfigProvider with
      PbsmrtpipeConfigLoader with
      EngineCoreConfigLoader with
      JobRunnerProvider with
      EulaServiceProvider with
      DataSetServiceProvider with
      JobsDaoActorProvider with
      JobsDaoProvider with
      TestDalProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider with
      SetBindings with
      ActorRefFactoryProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = if (jwt == INVALID_JWT) None else Some(UserRecord(jwt))
    })

    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(system)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.projectService().prefixedRoutes
  val dbURI = TestProviders.dbURI()

  def dbSetup() = {
    println("Running db setup")
    logger.info(s"Running tests from db-uri ${dbURI}")
    runSetup(dao)
    println(s"completed setting up database ${dbURI}")
  }

  textFragment("creating database tables")
  step(dbSetup())

  "EULA service" should {
    "return an empty list of EULAs" in {
      Get("/smrt-base/eula") ~> totalRoutes ~> check {
        val eulas = responseAs[Seq[EulaRecord]]
        eulas must beEmpty
      }
    }
    "accept the EULA" in {
      val params = EulaAcceptance("smrtlinktest", "4.0.0", true, false)
      Post("/smrt-base/eula", params) ~> totalRoutes ~> check {
        val eula = responseAs[EulaRecord]
        eula.user must beEqualTo("smrtlinktest")
      }
    }
    "retrieve the list of EULAs again" in {
      Get("/smrt-base/eula") ~> totalRoutes ~> check {
        val eulas = responseAs[Seq[EulaRecord]]
        eulas.size must beEqualTo(1)
      }
    }
    "retrieve the new EULA directly" in {
      Get("/smrt-base/eula/4.0.0") ~> totalRoutes ~> check {
        val eula = responseAs[EulaRecord]
        eula.smrtlinkVersion must beEqualTo("4.0.0")
        eula.user must beEqualTo("smrtlinktest")
        eula.enableInstallMetrics must beTrue
        eula.enableJobMetrics must beFalse
      }
    }
  }
}
