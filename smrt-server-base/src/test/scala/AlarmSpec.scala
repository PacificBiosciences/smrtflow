import java.nio.file.Paths

import com.pacbio.common.actors._
import com.pacbio.common.alarms._
import com.pacbio.common.auth.{Authenticator, AuthenticatorImplProvider, JwtUtils, JwtUtilsProvider}
import com.pacbio.common.dependency.StringConfigProvider
import com.pacbio.common.file.{FileSystemUtil, FileSystemUtilProvider}
import com.pacbio.common.models._
import com.pacbio.common.services.AlarmServiceProvider
import com.pacbio.secondary.smrtlink.alarms.{JobDirectoryAlarmRunnerProvider, TmpDirectoryAlarmRunnerProvider}
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.testkit.Specs2RouteTest

class AlarmSpec
  extends Specification
  with Mockito
  with Directives
  with Specs2RouteTest
  with NoTimeConversions {

  sequential

  import Authenticator._
  import AlarmSeverity._
  import PacBioJsonProtocol._

  val TEST_JOB_DIR = "/test/job/dir"

  val mockFileSystemUtil = mock[FileSystemUtil]

  object TestProviders extends
      AlarmComposer with
      StringConfigProvider with
      AlarmServiceProvider with
      JobDirectoryAlarmRunnerProvider with
      TmpDirectoryAlarmRunnerProvider with
      InMemoryAlarmDaoProvider with
      FileSystemUtilProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      ActorSystemProvider {

    import com.pacbio.common.dependency.Singleton

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

  // tmp dir at 50% capacity
  val tmpPath = TestProviders.tmpDirectoryAlarmRunner().path
  mockFileSystemUtil.getFreeSpace(tmpPath) returns 50
  mockFileSystemUtil.getTotalSpace(tmpPath) returns 100

  // job dir at 100% capacity
  mockFileSystemUtil.getFreeSpace(Paths.get(TEST_JOB_DIR)) returns 0
  mockFileSystemUtil.getTotalSpace(Paths.get(TEST_JOB_DIR)) returns 100

  val tmpId = "smrtlink.alarms.tmp_dir"
  val jobId = "smrtlink.alarms.job_dir"

  "Alarm Service" should {
    "return all alarms" in {
      Get("/smrt-base/alarm") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val alarms = responseAs[Seq[Alarm]]
        alarms.size === 2
        alarms.find(_.id == tmpId) must beSome
        alarms.find(_.id == jobId) must beSome
      }
    }

    "return all alarm statuses" in {
      Get("/smrt-base/alarm/status") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val statuses = responseAs[Seq[AlarmStatus]]
        statuses.size === 2
        statuses.find(_.id == tmpId) === Some(AlarmStatus(tmpId, 0.5, Some("Tmp dir is 50% full."), CLEAR))
        statuses.find(_.id == jobId) === Some(AlarmStatus(jobId, 1.0, Some("Job dir is 100% full."), CRITICAL))
      }
    }

    "return a specific alarm" in {
      Get(s"/smrt-base/alarm/$tmpId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val alarm = responseAs[Alarm]
        alarm === Alarm(
          tmpId,
          "Temp Dir Disk Space",
          "Monitors disk space usage in the tmp dir")
      }
    }

    "return a specific alarm status" in {
      Get(s"/smrt-base/alarm/$jobId/status") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val alarmStatus = responseAs[AlarmStatus]
        alarmStatus === AlarmStatus(jobId, 1.0, Some("Job dir is 100% full."), CRITICAL)
      }
    }

    "update an alarm status" in {
      // tmp dir at 90% capacity
      mockFileSystemUtil.getFreeSpace(tmpPath) returns 10

      Get(s"/smrt-base/alarm/$tmpId/status") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val alarmStatus = responseAs[AlarmStatus]
        alarmStatus === AlarmStatus(tmpId, 0.9, Some("Tmp dir is 90% full."), WARN)
      }
    }
  }
}
