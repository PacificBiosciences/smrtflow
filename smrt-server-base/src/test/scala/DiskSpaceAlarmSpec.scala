import java.nio.file.Paths

import com.pacbio.common.actors._
import com.pacbio.common.actors.alarms.{DiskSpaceAlarmProvider, AlarmComposer, DiskSpaceAlarm}
import com.pacbio.common.auth.{Authenticator, JwtUtils, JwtUtilsProvider, AuthenticatorImplProvider}
import com.pacbio.common.dependency.StringConfigProvider
import com.pacbio.common.file.{FileSystemUtil, FileSystemUtilProvider}
import com.pacbio.common.models._
import com.pacbio.common.services.AlarmServiceProvider
import com.pacbio.common.time.{FakeClock, FakeClockProvider}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.specs2.mock._
import org.specs2.mutable.{Specification, BeforeAfter}
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.testkit.Specs2RouteTest

class DiskSpaceAlarmSpec
  extends Specification
  with Mockito
  with Directives
  with Specs2RouteTest
  with NoTimeConversions {

  sequential

  import Authenticator._
  import DiskSpaceAlarm._
  import AlarmSeverity._
  import PacBioJsonProtocol._

  val TEST_JOB_DIR = "/test/job/dir"
  val TEST_SCHEDULE = "0/5 * * * * ?" // run every 5 seconds

  val mockFileSystemUtil = mock[FileSystemUtil]

  object TestProviders extends
      AlarmComposer with
      StringConfigProvider with
      AlarmServiceProvider with
      DiskSpaceAlarmProvider with
      InMemoryAlarmDaoProvider with
      FileSystemUtilProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider with
      ActorSystemProvider {

    import com.pacbio.common.dependency.Singleton

    override val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = Some(UserRecord(jwt))
    })
    override val actorSystemName = Some("TestSystem")
    override val fileSystemUtil = Singleton(() => mockFileSystemUtil)
    override val configString = Singleton(() =>
      s"""
        |akka {
        |  quartz {
        |    schedules {
        |      $NAME {
        |        description = "Test disk space alarm job. Runs every 5 seconds."
        |        expression = "$TEST_SCHEDULE"
        |      }
        |    }
        |  }
        |}
        |
        |smrtflow {
        |  engine {
        |    jobRootDir = "$TEST_JOB_DIR"
        |  }
        |}
      """.stripMargin
    )
  }

  trait SchedulerContext extends BeforeAfter {
    override def before: Any = ()
    override def after: Any = QuartzSchedulerExtension(TestProviders.actorSystem()).shutdown()
  }

  "Disk Space Alarm" should {
    "check available space" in new SchedulerContext {

      val credentials = RawHeader(JWT_HEADER, "username")
      val clock = TestProviders.clock().asInstanceOf[FakeClock]
      val routes = TestProviders.alarmService().prefixedRoutes

      Get("/smrt-base/alarm/metrics") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metrics = responseAs[Set[AlarmMetric]]
        metrics must beEmpty
      }

      // tmp dir at 50% capacity
      mockFileSystemUtil.getFreeSpace(Paths.get(TMP_DIR)) returns 100
      mockFileSystemUtil.getTotalSpace(Paths.get(TMP_DIR)) returns 200

      // job dir at 100% capacity
      mockFileSystemUtil.getFreeSpace(Paths.get(TEST_JOB_DIR)) returns 0
      mockFileSystemUtil.getTotalSpace(Paths.get(TEST_JOB_DIR)) returns 200

      val createdAt = clock.dateNow()
      TestProviders.initAlarms === Set(NAME)
      clock.step()

      Get("/smrt-base/alarm/metrics") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metrics = responseAs[Set[AlarmMetric]]
        metrics.size === 2
        metrics.find(_.id == TMP_DIR_ID) must beSome
        metrics.find(_.id == JOB_DIR_ID) must beSome
      }

      val updatedAt = clock.dateNow()
      Thread.sleep(10 * 1000)

      Get("/smrt-base/alarm/metrics") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metrics = responseAs[Set[AlarmMetric]]

        val tmpMetric = metrics.find(_.id == TMP_DIR_ID).get
        tmpMetric === AlarmMetric(
          TMP_DIR_ID,
          "Temp Dir Free Space",
          "Monitors free disk space in the /tmp dir",
          TagCriteria(hasAll = Set(DISK_SPACE_TAG, TMP_DIR_ID)),
          MetricType.LATEST,
          Map(WARN -> .9, ERROR -> .95, CRITICAL -> .99),
          windowSeconds = None,
          AlarmSeverity.CLEAR,
          metricValue = 0.5,
          createdAt,
          Some(updatedAt))

        val jobMetric = metrics.find(_.id == JOB_DIR_ID).get
        jobMetric === AlarmMetric(
          JOB_DIR_ID,
          "Job Dir Free Space",
          "Monitors free disk space in the jobs dir",
          TagCriteria(hasAll = Set(DISK_SPACE_TAG, JOB_DIR_ID)),
          MetricType.LATEST,
          Map(WARN -> .9, ERROR -> .95, CRITICAL -> .99),
          windowSeconds = None,
          AlarmSeverity.CRITICAL,
          metricValue = 1.0,
          createdAt,
          Some(updatedAt))
      }

      Get(s"/smrt-base/alarm/metrics/$TMP_DIR_ID/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[AlarmMetricUpdate]]

        updates.head.copy(updateId = 0) === AlarmMetricUpdate(
          updateValue = 0.5,
          Set(DISK_SPACE_TAG, TMP_DIR_ID),
          note = None,
          updateId = 0,
          updatedAt)
      }

      Get(s"/smrt-base/alarm/metrics/$JOB_DIR_ID/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[AlarmMetricUpdate]]

        updates.head.copy(updateId = 0) === AlarmMetricUpdate(
          updateValue = 1.0,
          Set(DISK_SPACE_TAG, JOB_DIR_ID),
          note = None,
          updateId = 0,
          updatedAt)
      }
    }
  }
}
