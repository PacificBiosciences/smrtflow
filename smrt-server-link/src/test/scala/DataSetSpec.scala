import akka.actor.ActorRefFactory
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.auth.Authenticator._
import com.pacbio.common.auth.{AuthenticatorImplProvider, JwtUtils, JwtUtilsProvider}
import com.pacbio.common.dependency.{SetBindings, Singleton}
import com.pacbio.common.models.UserRecord
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{DataSetServiceProvider, JobRunnerProvider}
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.testkit.Specs2RouteTest
import slick.driver.PostgresDriver.api._

import scala.concurrent.Await
import scala.concurrent.duration._


class DataSetSpec extends Specification
with NoTimeConversions
with Specs2RouteTest
with SetupMockData
with JobServiceConstants with TestUtils{

  sequential

  import SmrtLinkJsonProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  object TestProviders extends
      ServiceComposer with
      SmrtLinkConfigProvider with
      PbsmrtpipeConfigLoader with
      EngineCoreConfigLoader with
      JobRunnerProvider with
      DataSetServiceProvider with
      JobsDaoActorProvider with
      EventManagerActorProvider with
      JobsDaoProvider with
      TestDalProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider with
      SetBindings with
      ActorRefFactoryProvider {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = Some(UserRecord(jwt))
    })

    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(system)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.dataSetService().prefixedRoutes

  step(setupDb(TestProviders.dbConfig))
  step(runInsertAllMockData(dao))

  "DataSetService should list" should {
    "Secondary analysis DataSets Types resources" in {
      Get(s"/$ROOT_SERVICE_PREFIX/dataset-types") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis Subread DataSetsType resource" in {
      Get(s"/$ROOT_SERVICE_PREFIX/dataset-types/subreads") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dst = responseAs[ServiceDataSetMetaType]
        dst.id must be_==("PacBio.DataSet.SubreadSet")
      }
    }
    "Secondary analysis Subread DataSetsType resource" in {
      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis Subread DataSetsType resources by projectId" in {
      val credentials = RawHeader(JWT_HEADER, MOCK_USER_LOGIN)

      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads?projectId=$getMockProjectId") ~> addHeader(credentials) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        subreads.size === 2
        subreads.count(_.projectId == getMockProjectId) === 2
      }

      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads?projectId=$GEN_PROJECT_ID") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        subreads.size === 2
        subreads.count(_.projectId == GEN_PROJECT_ID) === 2
      }

      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads") ~> addHeader(credentials) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        subreads.size === 4
        subreads.count(_.projectId == getMockProjectId) === 2
        subreads.count(_.projectId == GEN_PROJECT_ID) === 2
      }

      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads?projectId=99999999") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        subreads.size === 0
      }
    }
    "Secondary analysis Subread DataSet resource by id" in {
      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads/1") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subread = responseAs[SubreadServiceDataSet]
        subread.jobId === 1
      }
    }
    "Secondary analysis Subread DataSet resource by id" in {
      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads/1/details") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[String].parseJson
        resp.asJsObject().fields("MetaType") === JsString("PacBio.DataSet.SubreadSet")
      }
    }
    "Secondary analysis Reference DataSetsType resource" in {
      Get(s"/$ROOT_SERVICE_PREFIX/dataset-types/references") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis Reference DataSetsType resource" in {
      Get(s"/$ROOT_SERVICE_PREFIX/dataset-types/alignments") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis Subread Reference resource" in {
      Get(s"/$ROOT_SERVICE_PREFIX/datasets/references") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        //val dst = responseAs[DataSetType]
        //dst.id must be_==("pacbio.datasets.subread")
      }
    }
    // TODO(smcclellan): Turn test case on once dataset ids use autoinc (see TODOs in JobsDao)
//    "Secondary analysis Reference DataSet resource by id" in {
//      Get(s"/$ROOT_SERVICE_PREFIX/datasets/references/1") ~> totalRoutes ~> check {
//        status.isSuccess must beTrue
//        //val dst = responseAs[DataSetType]
//        //dst.id must be_==("pacbio.datasets.subread")
//      }
//    }
    "Secondary analysis Hdf Subread DataSet resources" in {
      Get(s"/$ROOT_SERVICE_PREFIX/datasets/hdfsubreads") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        //val dst = responseAs[DataSetType]
        //dst.id must be_==("pacbio.datasets.subread")
      }
    }
    // TODO(smcclellan): Turn test case on once dataset ids use autoinc (see TODOs in JobsDao)
//    "Secondary analysis Hdf Subread DataSet resources" in {
//      Get(s"/$ROOT_SERVICE_PREFIX/datasets/hdfsubreads/1/details") ~> totalRoutes ~> check {
//        status.isSuccess must beTrue
//        //val dst = responseAs[DataSetType]
//        //dst.id must be_==("pacbio.datasets.subread")
//      }
//    }
  }
}
