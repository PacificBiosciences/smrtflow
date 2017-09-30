import java.nio.file.Files

import com.pacbio.secondary.smrtlink.actors.{JobsDao, SmrtLinkTestDalProvider}
import com.pacbio.secondary.smrtlink.analysis.jobs.PacBioIntJobResolver
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import org.specs2.mutable.Specification
import slick.driver.PostgresDriver.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class JobsDaoSpec extends Specification with TestUtils with SetupMockData {
  sequential

  object TestProviders extends SmrtLinkTestDalProvider {}

  val tmpJobDir = Files.createTempDirectory("jobs-dao-spec")
  val jobResolver = new PacBioIntJobResolver(tmpJobDir)

  val dao =
    new JobsDao(TestProviders.dbConfig.toDatabase, jobResolver, None)

  val db: Database = dao.db
  val timeout = FiniteDuration(10, "seconds")

  step(setupDb(TestProviders.dbConfig))
  step(runInsertAllMockData(dao))

  def validateSize(f: => Future[Int], x: Int) = {
    Await.result(f, timeout) === x
  }

  "Sanity Test" should {
    "Can connect to database" in {
      true === true
    }
    "Sanity DAO insertion test" in {
      validateSize(dao.getDataStoreFiles().map(_.length), 8)
    }
    "Get DataSetMeta" in {
      validateSize(dao.getDataSetMetas(activity = None).map(_.length), 7)
    }
    "Get SubreadSets" in {
      validateSize(dao.getSubreadDataSets().map(_.length), 2)
    }
    "Get ReferenceSets" in {
      validateSize(dao.getReferenceDataSets().map(_.length), 4)
    }
    "Get BarcodeSets" in {
      validateSize(dao.getBarcodeDataSets().map(_.length), 1)
    }
  }
  step(cleanUpJobDir(tmpJobDir))
}
