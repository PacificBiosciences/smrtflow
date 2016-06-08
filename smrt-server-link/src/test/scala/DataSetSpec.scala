import akka.actor.ActorRefFactory
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.database.TestDatabaseProvider
import com.pacbio.common.dependency.{SetBindings, Singleton}
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors.{JobsDao, JobsDaoActorProvider, JobsDaoProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.DataSetServiceProvider
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import org.specs2.mutable.Specification
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.FiniteDuration


class DataSetSpec extends Specification
with Specs2RouteTest
with SetupMockData
with JobServiceConstants {
  import SmrtLinkJsonProtocols._

  sequential

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  object TestProviders extends
  ServiceComposer with
  DataSetServiceProvider with
  JobsDaoActorProvider with
  JobsDaoProvider with
  SmrtLinkConfigProvider with
  TestDatabaseProvider with
  PbsmrtpipeConfigLoader with
  EngineCoreConfigLoader with
  FakeClockProvider with
  SetBindings with
  ActorRefFactoryProvider {
    override val actorRefFactory: Singleton[ActorRefFactory] = Singleton(system)
  }

  override val dao: JobsDao = TestProviders.jobsDao()
  val totalRoutes = TestProviders.dataSetService().prefixedRoutes
  val dbURI = TestProviders.getFullURI(TestProviders.dbURI())

  def dbSetup() = {
    println("Running db setup")
    logger.info(s"Running tests from db-uri $dbURI")
    runSetup(dbURI)
    println(s"completed setting up database $dbURI")
  }

  textFragment("creating database tables")
  step(dbSetup())

  "Service list" should {
    var id = -1

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
        val resp = responseAs[Seq[SubreadServiceDataSet]]
        resp.isEmpty === false
        id = resp.head.id
        ok
      }
    }
    "Secondary analysis Subread Schema resource" in {
      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads/_schema") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        //val dst = responseAs[DataSetType]
        //dst.id must be_==("pacbio.datasets.subread")
      }
    }
    "Secondary analysis Subread DataSet resource by id" in {
      Get(s"/$ROOT_SERVICE_PREFIX/datasets/subreads/$id") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        //val dst = responseAs[DataSetType]
        //dst.id must be_==("pacbio.datasets.subread")
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
    "Secondary analysis Reference Schema resource" in {
      Get(s"/$ROOT_SERVICE_PREFIX/datasets/references/_schema") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
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
