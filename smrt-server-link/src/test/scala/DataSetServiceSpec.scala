import java.nio.file.Files

import akka.actor.ActorRefFactory
import com.pacbio.secondary.smrtlink.auth.Authenticator._
import com.pacbio.secondary.smrtlink.auth.{
  AuthenticatorImplProvider,
  JwtUtils,
  JwtUtilsProvider
}
import com.pacbio.secondary.smrtlink.dependency.{SetBindings, Singleton}
import com.pacbio.secondary.smrtlink.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.analysis.configloaders.{
  EngineCoreConfigLoader,
  PbsmrtpipeConfigLoader
}
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors.{ActorRefFactoryProvider, _}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.{
  DataSetServiceProvider,
  ServiceComposer
}
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import spray.testkit.Specs2RouteTest
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

class DataSetServiceSpec
    extends Specification
    with NoTimeConversions
    with Specs2RouteTest
    with SetupMockData
    with JobServiceConstants
    with TestUtils {

  sequential

  val tmpJobDir = Files.createTempDirectory("dataset-spec")
  val tmpEngineConfig = EngineConfig(1, None, tmpJobDir, debugMode = true)

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  object TestProviders
      extends ServiceComposer
      with SmrtLinkTestDalProvider
      with SmrtLinkConfigProvider
      with PbsmrtpipeConfigLoader
      with EngineCoreConfigLoader
      with DataSetServiceProvider
      with EventManagerActorProvider
      with JobsDaoProvider
      with AuthenticatorImplProvider
      with JwtUtilsProvider
      with FakeClockProvider
      with SetBindings
      with ActorRefFactoryProvider {

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() =>
      new JwtUtils {
        override def parse(jwt: String): Option[UserRecord] =
          Some(UserRecord(jwt))
    })

    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(
      system)

    override val jobEngineConfig: Singleton[EngineConfig] =
      Singleton(tmpEngineConfig)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  override val db: Database = dao.db
  val totalRoutes = TestProviders.dataSetService().prefixedRoutes

  step(setupDb(TestProviders.dbConfig))
  step(runInsertAllMockData(dao))

  "DataSetService should list" should {
    "Secondary analysis DataSets Types resources" in {
      Get(s"/$ROOT_SA_PREFIX/dataset-types") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis Subread DataSetsType resource" in {
      Get(s"/$ROOT_SA_PREFIX/dataset-types/subreads") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dst = responseAs[ServiceDataSetMetaType]
        dst.id must be_==("PacBio.DataSet.SubreadSet")
      }
    }
    "Sanity DAO insertion test" in {
      val timeout = 10.seconds
      val datasets = Await.result(dao.getSubreadDataSets(), timeout)
      datasets.length == 2
    }
    "Secondary analysis Get SubreadSet list" in {
      Get(s"/$ROOT_SA_PREFIX/datasets/subreads") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        subreads.size === 2
      }
    }
    "Secondary analysis SubreadSets by TEST/MOCK PROJECT ID" in {
      val credentials = RawHeader(JWT_HEADER, MOCK_USER_LOGIN)

      val fx: Future[Option[Int]] =
        dao.getProjects().map(_.find(_.name == TEST_PROJECT_NAME).map(_.id))

      val testProjectId: Option[Int] = Await.result(fx, 5.seconds)

      testProjectId must beSome

      val projectId = testProjectId.get

      println(s"Mock/Test Project Id $projectId")

      Get(s"/$ROOT_SA_PREFIX/datasets/subreads?projectId=$projectId") ~> addHeader(
        credentials) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        // Is this a bug on how the project Ids are assigned?
        //subreads.size === 2
        //subreads.count(_.projectId == projectId) === 2
      }

      Get(s"/$ROOT_SA_PREFIX/datasets/subreads?projectId=$GEN_PROJECT_ID") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        subreads.size === 2
        subreads.count(_.projectId == GEN_PROJECT_ID) === 2
      }

      Get(s"/$ROOT_SA_PREFIX/datasets/subreads") ~> addHeader(credentials) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        subreads.size === 2
        //subreads.count(_.projectId == projectId) === 2
        subreads.count(_.projectId == GEN_PROJECT_ID) === 2
      }

      Get(s"/$ROOT_SA_PREFIX/datasets/subreads?projectId=99999999") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subreads = responseAs[Seq[SubreadServiceDataSet]]
        subreads.size === 0
      }
    }
    "Secondary analysis Subread DataSet resource by id" in {
      Get(s"/$ROOT_SA_PREFIX/datasets/subreads/1") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subread = responseAs[SubreadServiceDataSet]
        subread.jobId === 1
      }
    }
    "Update SubreadSet resource bioSampleName and wellSampleName" in {
      val opts = DataSetUpdateRequest(bioSampleName = Some("hobbit"),
                                      wellSampleName = Some("hobbit DNA"))
      Put(s"/$ROOT_SA_PREFIX/datasets/subreads/1", opts) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
      Get(s"/$ROOT_SA_PREFIX/datasets/subreads/1") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val subread = responseAs[SubreadServiceDataSet]
        subread.bioSampleName must beEqualTo("hobbit")
        subread.wellSampleName must beEqualTo("hobbit DNA")
      }
    }
    "Secondary analysis Subread DataSet resource by id" in {
      Get(s"/$ROOT_SA_PREFIX/datasets/subreads/1/details") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[String].parseJson
        resp.asJsObject().fields("MetaType") === JsString(
          "PacBio.DataSet.SubreadSet")
      }
    }
    "Secondary analysis Reference DataSetsType resource" in {
      Get(s"/$ROOT_SA_PREFIX/dataset-types/references") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis Reference DataSetsType resource" in {
      Get(s"/$ROOT_SA_PREFIX/dataset-types/alignments") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Secondary analysis ReferenceSet resource" in {
      Get(s"/$ROOT_SA_PREFIX/datasets/references") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val datasets = responseAs[Seq[ReferenceServiceDataSet]]
        datasets.size === 4
      }
    }
    "Secondary analysis Reference DataSet resource by UUID" in {
      Get(
        s"/$ROOT_SA_PREFIX/datasets/references/f86beef0-9666-4627-b403-751f32a67f29") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val dataset = responseAs[ReferenceServiceDataSet]
        dataset.name === "Chloroflexus_aggregans_DSM9485"
      }
    }
    "Secondary analysis Hdf Subread DataSet resources" in {
      Get(s"/$ROOT_SA_PREFIX/datasets/hdfsubreads") ~> totalRoutes ~> check {
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
  step(cleanUpJobDir(tmpEngineConfig.pbRootJobDir))
}
