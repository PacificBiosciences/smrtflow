import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.actor.ActorRefFactory
import spray.json._
import com.pacbio.secondary.smrtlink.auth._
import com.pacbio.secondary.smrtlink.dependency.{SetBindings, Singleton}
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors
import com.pacbio.secondary.smrtlink.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.analysis.configloaders.{
  EngineCoreConfigLoader,
  PbsmrtpipeConfigLoader
}
import com.pacbio.secondary.smrtlink.{JobServiceConstants, SmrtLinkConstants}
import com.pacbio.secondary.smrtlink.actors.{
  ActorRefFactoryProvider,
  SmrtLinkDalProvider,
  _
}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import slick.driver.PostgresDriver.api.Database

/**
  * This spec has been updated to support multiple runs (i.e.,
  * not necessary to drop and create+migrate the db) This should be re-evaluated.
  *
  */
class EulaServiceSpec
    extends Specification
    with Specs2RouteTest
    with SetupMockData
    with PacBioServiceErrors
    with JobServiceConstants
    with SmrtLinkConstants
    with TestUtils {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  sequential

  val INVALID_JWT = "invalid.jwt"

  val testSmrtLinkVersion = "1.2.3"

  trait TestEulaServiceProvider {
    this: JobsDaoProvider
      with SmrtLinkTestDalProvider
      with SmrtLinkConfigProvider
      with ServiceComposer =>

    val eulaService: Singleton[EulaService] =
      Singleton { () =>
        new EulaService(Some(testSmrtLinkVersion), jobsDao())
      }

    addService(eulaService)
  }

  object TestProviders
      extends ServiceComposer
      with ProjectServiceProvider
      with SmrtLinkTestDalProvider
      with SmrtLinkConfigProvider
      with PbsmrtpipeConfigLoader
      with EngineCoreConfigLoader
      with TestEulaServiceProvider
      with DataSetServiceProvider
      with EventManagerActorProvider
      with JobsDaoProvider
      with AuthenticatorImplProvider
      with JwtUtilsProvider
      with FakeClockProvider
      with SetBindings
      with ActorRefFactoryProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() =>
      new JwtUtils {
        override def parse(jwt: String): Option[UserRecord] =
          if (jwt == INVALID_JWT) None else Some(UserRecord(jwt))
    })

    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(
      system)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.eulaService().prefixedRoutes

  step(setupDb(TestProviders.dbConfig))

  "EULA service" should {
    "return an empty list of EULAs" in {
      Get("/smrt-base/eula") ~> totalRoutes ~> check {
        val eulas = responseAs[Seq[EulaRecord]]
        //eulas must beEmpty
        status.isSuccess must beTrue
      }
    }
    "accept the EULA" in {
      val params = EulaAcceptance("smrtlinktest", enableInstallMetrics = true)
      Post("/smrt-base/eula", params) ~> totalRoutes ~> check {
        val eula = responseAs[EulaRecord]
        eula.user must beEqualTo("smrtlinktest")
        eula.smrtlinkVersion must beEqualTo(testSmrtLinkVersion)
      }
    }
    "retrieve the list of EULAs again" in {
      Get("/smrt-base/eula") ~> totalRoutes ~> check {
        val eulas = responseAs[Seq[EulaRecord]]
        eulas.size must beEqualTo(1)
      }
    }
    "retrieve the new EULA directly" in {
      Get(s"/smrt-base/eula/$testSmrtLinkVersion") ~> totalRoutes ~> check {
        val eula = responseAs[EulaRecord]
        eula.smrtlinkVersion must beEqualTo(testSmrtLinkVersion)
        eula.user must beEqualTo("smrtlinktest")
        eula.enableInstallMetrics must beTrue
        eula.enableJobMetrics must beFalse
      }
    }
  }
}
