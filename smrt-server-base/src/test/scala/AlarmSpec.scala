import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.SetBindings
import com.pacbio.common.models._
import com.pacbio.common.services.AlarmServiceProvider
import com.pacbio.common.time.{FakeClock, FakeClockProvider}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.http.OAuth2BearerToken
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// TODO(smcclellan): Refactor this into multiple specs, for the spray routing, the DAO, and the database interactions
class AlarmSpec
  extends Specification
  with Directives
  with Specs2RouteTest
  with HttpService
  with NoTimeConversions {

  sequential

  import PacBioJsonProtocol._
  import Authenticator._

  def actorRefFactory = system

  val latestMetricId = "latest_metric"
  val sumMetricId = "sum_metric"
  val avgMetricId = "avg_metric"
  val maxMetricId = "max_metric"

  val severityLevels: Map[AlarmSeverity.AlarmSeverity, Double] = Map(
    AlarmSeverity.WARN -> 1.0,
    AlarmSeverity.ERROR -> 2.0,
    AlarmSeverity.CRITICAL -> 3.0
  )

  // TODO(smcclellan): Test coverage for TagCriteria

  val latestMetricCreate = AlarmMetricCreateMessage(
    latestMetricId,
    "LatestMetric",
    "Metric of type LATEST",
    TagCriteria(hasAll = Set(latestMetricId)),
    MetricType.LATEST,
    severityLevels,
    None)

  val sumMetricCreate = AlarmMetricCreateMessage(
    sumMetricId,
    "SumMetric",
    "Metric of type SUM",
    TagCriteria(hasAll = Set(sumMetricId)),
    MetricType.SUM,
    severityLevels,
    Some(1))

  val avgMetricCreate = AlarmMetricCreateMessage(
    avgMetricId,
    "AvgMetric",
    "Metric of type AVERAGE",
    TagCriteria(hasAll = Set(avgMetricId)),
    MetricType.AVERAGE,
    severityLevels,
    Some(1))

  val maxMetricCreate = AlarmMetricCreateMessage(
    maxMetricId,
    "MaxMetric",
    "Metric of type MAX",
    TagCriteria(hasAll = Set(maxMetricId)),
    MetricType.MAX,
    severityLevels,
    Some(1))

  val readUserLogin = "reader"
  val adminUserLogin = "admin"

  val invalidJwt = "invalid.jwt"

  object TestProviders extends
  SetBindings with
  AlarmServiceProvider with
  InMemoryAlarmDaoProvider with
  AuthenticatorImplProvider with
  JwtUtilsProvider with
  FakeClockProvider {

    import com.pacbio.common.dependency.Singleton

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = if (jwt == invalidJwt) None else Some {
        if (jwt == adminUserLogin) UserRecord(jwt, Some("PbAdmin")) else UserRecord(jwt)
      }
    })
  }

  val dao = TestProviders.alarmDao()
  val authenticator = TestProviders.authenticator()
  val clock = TestProviders.clock().asInstanceOf[FakeClock]

  val routes = TestProviders.alarmService().prefixedRoutes

  trait daoSetup extends Scope {
    TestProviders.alarmDao().asInstanceOf[InMemoryAlarmDao].clear()

    Await.ready(Future.sequence(Seq(
      TestProviders.alarmDao().createAlarmMetric(latestMetricCreate),
      TestProviders.alarmDao().createAlarmMetric(sumMetricCreate),
      TestProviders.alarmDao().createAlarmMetric(avgMetricCreate),
      TestProviders.alarmDao().createAlarmMetric(maxMetricCreate)
    )), 10.seconds)
  }

  "Alarm Service" should {

    "return a list of alarm metrics" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, readUserLogin)
      Get("/smrt-base/alarm/metrics") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metrics = responseAs[Set[AlarmMetric]]
        metrics.size === 4
        metrics.map(_.id) === Set(latestMetricId, sumMetricId, avgMetricId, maxMetricId)
        metrics.forall(_.severity == AlarmSeverity.CLEAR) === true
        metrics.forall(_.metricValue == 0.0) === true
        metrics.forall(_.createdAt == clock.dateNow()) === true
      }
    }

    "return a specific alarm metric" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, readUserLogin)
      Get(s"/smrt-base/alarm/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[AlarmMetric].id === latestMetricId
      }
    }

    "create a new alarm metric" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, adminUserLogin)
      val newId = "new_id"
      val message = latestMetricCreate.copy(id = newId)
      clock.step()
      Post("/smrt-base/alarm/metrics", message) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[AlarmMetric].id === newId
        responseAs[AlarmMetric].createdAt === clock.dateNow()
        responseAs[AlarmMetric].lastUpdate === None
      }
      Get(s"/smrt-base/alarm/metrics/$newId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[AlarmMetric].id === newId
        responseAs[AlarmMetric].createdAt === clock.dateNow()
        responseAs[AlarmMetric].lastUpdate === None
      }
    }

    "update alarm metrics" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, adminUserLogin)
      val message1 = AlarmMetricUpdateMessage(1.0, Set(latestMetricId, sumMetricId, avgMetricId, maxMetricId))
      val message3 = AlarmMetricUpdateMessage(3.0, Set(latestMetricId, sumMetricId, avgMetricId, maxMetricId))
      val message2 = AlarmMetricUpdateMessage(2.0, Set(latestMetricId, sumMetricId, avgMetricId, maxMetricId))

      // Post updates to each metric in order of 1 -> 3 -> 2
      //
      // For LATEST metric, result should be 1 -> 3 -> 2
      // For SUM metric, result should be 1 -> 4 -> 6
      // For AVERAGE metric, result should be 1 -> 2 -> 2
      // For MAX metric, result should be 1 -> 3 -> 3

      // Send update 1.0

      clock.reset(10)
      val t10 = clock.dateNow()
      Post("/smrt-base/alarm/updates", message1) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[AlarmMetricUpdate]
        update.updateValue === 1.0
        update.timestamp === t10
      }
      Get(s"/smrt-base/alarm/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 1.0
        metric.severity === AlarmSeverity.WARN
        metric.lastUpdate === Some(t10)
      }
      Get(s"/smrt-base/alarm/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 1.0
        metric.severity === AlarmSeverity.WARN
        metric.lastUpdate === Some(t10)
      }
      Get(s"/smrt-base/alarm/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 1.0
        metric.severity === AlarmSeverity.WARN
        metric.lastUpdate === Some(t10)
      }
      Get(s"/smrt-base/alarm/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 1.0
        metric.severity === AlarmSeverity.WARN
        metric.lastUpdate === Some(t10)
      }

      // Send update 3.0

      clock.reset(20)
      val t20 = clock.dateNow()
      Post(s"/smrt-base/alarm/updates", message3) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[AlarmMetricUpdate]
        update.updateValue === 3.0
        update.timestamp === t20
      }
      Get(s"/smrt-base/alarm/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 3.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t20)
      }
      Get(s"/smrt-base/alarm/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 4.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t20)
      }
      Get(s"/smrt-base/alarm/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t20)
      }
      Get(s"/smrt-base/alarm/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 3.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t20)
      }

      // Send update 2.0

      clock.reset(30)
      val t30 = clock.dateNow()
      Post(s"/smrt-base/alarm/updates", message2) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[AlarmMetricUpdate]
        update.updateValue === 2.0
        update.timestamp === t30
      }
      Get(s"/smrt-base/alarm/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 6.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 3.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }

      clock.reset(1005)
      // All metrics have a window of 1 second, the first updates were at t = 10 ms, 1005 ms - 10 ms < 1000 ms, so
      // recalculating should not change the metric values

      Get(s"/smrt-base/alarm/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 6.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 3.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }

      clock.reset(1015)
      // Recalculating now should drop the first update at t = 10 ms
      // Note: this does not apply to the LATEST metric

      Get(s"/smrt-base/alarm/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 5.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.5
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 3.0
        metric.severity === AlarmSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }

      clock.reset(1025)
      // Recalculating now should drop the second update

      Get(s"/smrt-base/alarm/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }

      clock.reset(1035)
      val t1035 = clock.dateNow()
      // Recalculating now should drop the third update

      Get(s"/smrt-base/alarm/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 2.0
        metric.severity === AlarmSeverity.ERROR
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/alarm/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 0.0
        metric.severity === AlarmSeverity.CLEAR
        metric.lastUpdate === None
      }
      Get(s"/smrt-base/alarm/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 0.0
        metric.severity === AlarmSeverity.CLEAR
        metric.lastUpdate === None
      }
      Get(s"/smrt-base/alarm/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[AlarmMetric]
        metric.metricValue === 0.0
        metric.severity === AlarmSeverity.CLEAR
        metric.lastUpdate === None
      }

      // Check the update records

      Get(s"/smrt-base/alarm/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[AlarmMetricUpdate]]
        updates.size === 3
        updates(0).timestamp === t10
        updates(0).updateValue === 1.0
        updates(1).timestamp === t20
        updates(1).updateValue === 3.0
        updates(2).timestamp === t30
        updates(2).updateValue === 2.0
      }
      Get(s"/smrt-base/alarm/metrics/$latestMetricId/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[AlarmMetricUpdate]]
        updates.size === 3
        updates(0).timestamp === t10
        updates(0).updateValue === 1.0
        updates(1).timestamp === t20
        updates(1).updateValue === 3.0
        updates(2).timestamp === t30
        updates(2).updateValue === 2.0
      }
      Get(s"/smrt-base/alarm/metrics/$sumMetricId/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[AlarmMetricUpdate]]
        updates.size === 0
      }
      Get(s"/smrt-base/alarm/metrics/$avgMetricId/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[AlarmMetricUpdate]]
        updates.size === 0
      }
      Get(s"/smrt-base/alarm/metrics/$maxMetricId/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[AlarmMetricUpdate]]
        updates.size === 0
      }
    }

    "return unhealthy messages" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, adminUserLogin)
      Post(s"/smrt-base/alarm/updates", AlarmMetricUpdateMessage(1.0, Set(latestMetricId))) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[AlarmMetricUpdate]
        update.updateValue === 1.0
      }
      Post(s"/smrt-base/alarm/updates", AlarmMetricUpdateMessage(2.0, Set(sumMetricId))) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[AlarmMetricUpdate]
        update.updateValue === 2.0
      }

      Get("/smrt-base/alarm") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metrics = responseAs[Seq[AlarmMetric]]
        metrics.length === 2
        metrics.map(_.id) === Set(latestMetricId, sumMetricId)
        metrics.map(_.metricValue) === Set(1.0, 2.0)
      }
    }

    "reject unauthorized users" in new daoSetup {
      Get("/smrt-base/alarm/metrics") ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val invalid = RawHeader(JWT_HEADER, invalidJwt)
      Get("/smrt-base/alarm/metrics") ~> addHeader(invalid) ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }
    }
  }
}
