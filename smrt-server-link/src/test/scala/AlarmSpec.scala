import java.nio.file.Paths

import akka.pattern._
import akka.util.Timeout
import com.pacbio.common.actors._
import com.pacbio.common.alarms._
import com.pacbio.common.auth.{Authenticator, AuthenticatorImplProvider, JwtUtils, JwtUtilsProvider}
import com.pacbio.common.dependency.StringConfigProvider
import com.pacbio.common.file.{FileSystemUtil, FileSystemUtilProvider}
import com.pacbio.common.models._
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.analysis.configloaders.{EngineCoreConfigLoader, PbsmrtpipeConfigLoader}
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.AlarmManagerRunnerActor.RunAlarms
import com.pacbio.secondary.smrtlink.actors.{AlarmDaoActorProvider, AlarmManagerRunnerProvider}
import com.pacbio.secondary.smrtlink.services.AlarmServiceProvider
import com.pacbio.secondary.smrtlink.alarms.{JobDirectoryAlarmRunnerProvider, TmpDirectoryAlarmRunnerProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration._
import scala.concurrent.Await

class AlarmSpec
  extends Specification
  with Mockito
  with Directives
  with Specs2RouteTest
  with NoTimeConversions with LazyLogging{

  sequential

  import Authenticator._
  import AlarmSeverity._
  import PacBioJsonProtocol._

  val TEST_JOB_DIR = "/test/job/dir"

  val mockFileSystemUtil = mock[FileSystemUtil]

  object TestProviders extends
      ServiceComposer with
      ActorRefFactoryProvider with
      ActorSystemProvider with
      FileSystemUtilProvider with
      EngineCoreConfigLoader with
      PbsmrtpipeConfigLoader with
      StringConfigProvider with
      SmrtLinkConfigProvider with
      AlarmDaoActorProvider with
      AlarmComposer with
      TmpDirectoryAlarmRunnerProvider with
      JobDirectoryAlarmRunnerProvider with
      AlarmManagerRunnerProvider with
      AlarmServiceProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider {

    import com.pacbio.common.dependency.Singleton

    //override

    override val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = Some(UserRecord(jwt))
    })
    override val actorSystemName = Some("TestSystem")
    override val fileSystemUtil = Singleton(() => mockFileSystemUtil)
    override val configString = Singleton(() =>
      s"""
        |smrtflow {
        |  engine {
        |    jobRootDir = "$TEST_JOB_DIR"
        |  }
        |}
      """.stripMargin
    )
  }

  val credentials = RawHeader(JWT_HEADER, "username")
  val routes = TestProviders.alarmService().prefixedRoutes
  val alarmManagerRunnerActor = TestProviders.alarmManagerRunnerActor()

  // tmp dir at 50% capacity
  val tmpPath = TestProviders.tmpDirectoryAlarmRunner().path
  mockFileSystemUtil.getFreeSpace(tmpPath) returns 50
  mockFileSystemUtil.getTotalSpace(tmpPath) returns 100

  // job dir at 100% capacity
  mockFileSystemUtil.getFreeSpace(Paths.get(TEST_JOB_DIR)) returns 0
  mockFileSystemUtil.getTotalSpace(Paths.get(TEST_JOB_DIR)) returns 100

  val tmpId = "smrtlink.alarms.tmp_dir"
  val jobId = "smrtlink.alarms.job_dir"

  def runSetup(): Unit = {
    logger.info("Running setup with init message to Alarm Manager")
    implicit val messageTimeout = Timeout(10.seconds)
    // the results are run async, so this might have reproducibility issues
    val f = (alarmManagerRunnerActor ? RunAlarms).mapTo[MessageResponse]
    val results = Await.result(f, 5.seconds)
    logger.info(s"Results $results")
  }

  step(runSetup())

  "Alarm Service" should {
    "return all alarms" in {
      Get("/smrt-link/alarms") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val alarms = responseAs[Seq[AlarmStatus]]
        alarms.size === 2
        alarms.find(_.id == tmpId) must beSome
        alarms.find(_.id == jobId) must beSome
      }
    }

    "return all alarm status for tmp dir alarm" in {
      Get("/smrt-link/alarms") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val statuses = responseAs[Seq[AlarmStatus]]
        statuses.size === 2
        statuses.find(_.id == tmpId) === Some(AlarmStatus(tmpId, 0.5, Some("Tmp dir is 50% full."), CLEAR))
        statuses.find(_.id == jobId) === Some(AlarmStatus(jobId, 1.0, Some("Job dir is 100% full."), CRITICAL))
      }
    }

    "return a specific alarm" in {
      Get(s"/smrt-link/alarms/$tmpId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val alarm = responseAs[AlarmStatus]
        alarm === AlarmStatus(tmpId, 0.5, Some("Tmp dir is 50% full."), CLEAR)
      }
    }

    "return a specific alarm status" in {
      Get(s"/smrt-link/alarms/$jobId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val alarmStatus = responseAs[AlarmStatus]
        alarmStatus === AlarmStatus(jobId, 1.0, Some("Job dir is 100% full."), CRITICAL)
      }
    }
  }
}
