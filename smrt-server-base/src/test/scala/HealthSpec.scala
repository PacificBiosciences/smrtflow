import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.SetBindings
import com.pacbio.common.models._
import com.pacbio.common.services.HealthServiceProvider
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
class HealthSpec
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

  val severityLevels: Map[HealthSeverity.HealthSeverity, Double] = Map(
    HealthSeverity.CAUTION -> 1.0,
    HealthSeverity.ALERT -> 2.0,
    HealthSeverity.CRITICAL -> 3.0
  )

  // TODO(smcclellan): Test coverage for TagCriteria

  val latestMetricCreate = HealthMetricCreateMessage(
    latestMetricId,
    "LatestMetric",
    "Metric of type LATEST",
    TagCriteria(hasAll = Set(latestMetricId)),
    MetricType.LATEST,
    severityLevels,
    None)

  val sumMetricCreate = HealthMetricCreateMessage(
    sumMetricId,
    "SumMetric",
    "Metric of type SUM",
    TagCriteria(hasAll = Set(sumMetricId)),
    MetricType.SUM,
    severityLevels,
    Some(1))

  val avgMetricCreate = HealthMetricCreateMessage(
    avgMetricId,
    "AvgMetric",
    "Metric of type AVERAGE",
    TagCriteria(hasAll = Set(avgMetricId)),
    MetricType.AVERAGE,
    severityLevels,
    Some(1))

  val maxMetricCreate = HealthMetricCreateMessage(
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
  HealthServiceProvider with
  InMemoryHealthDaoProvider with
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

  val dao = TestProviders.healthDao()
  val authenticator = TestProviders.authenticator()
  val clock = TestProviders.clock().asInstanceOf[FakeClock]

  val routes = TestProviders.healthService().prefixedRoutes

  trait daoSetup extends Scope {
    TestProviders.healthDao().asInstanceOf[InMemoryHealthDao].clear()

    Await.ready(Future.sequence(Seq(
      TestProviders.healthDao().createHealthMetric(latestMetricCreate),
      TestProviders.healthDao().createHealthMetric(sumMetricCreate),
      TestProviders.healthDao().createHealthMetric(avgMetricCreate),
      TestProviders.healthDao().createHealthMetric(maxMetricCreate)
    )), 10.seconds)
  }

  "Health Service" should {

    "return a list of health metrics" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, readUserLogin)
      Get("/smrt-base/health/metrics") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metrics = responseAs[Set[HealthMetric]]
        metrics.size === 4
        metrics.map(_.id) === Set(latestMetricId, sumMetricId, avgMetricId, maxMetricId)
        metrics.forall(_.severity == HealthSeverity.OK) === true
        metrics.forall(_.metricValue == 0.0) === true
        metrics.forall(_.createdAt == clock.dateNow()) === true
      }
    }

    "return a specific health metric" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, readUserLogin)
      Get(s"/smrt-base/health/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[HealthMetric].id === latestMetricId
      }
    }

    "create a new health metric" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, adminUserLogin)
      val newId = "new_id"
      val message = latestMetricCreate.copy(id = newId)
      clock.step()
      Post("/smrt-base/health/metrics", message) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[HealthMetric].id === newId
        responseAs[HealthMetric].createdAt === clock.dateNow()
        responseAs[HealthMetric].lastUpdate === None
      }
      Get(s"/smrt-base/health/metrics/$newId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[HealthMetric].id === newId
        responseAs[HealthMetric].createdAt === clock.dateNow()
        responseAs[HealthMetric].lastUpdate === None
      }
    }

    "update health metrics" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, adminUserLogin)
      val message1 = HealthMetricUpdateMessage(1.0, Set(latestMetricId, sumMetricId, avgMetricId, maxMetricId))
      val message3 = HealthMetricUpdateMessage(3.0, Set(latestMetricId, sumMetricId, avgMetricId, maxMetricId))
      val message2 = HealthMetricUpdateMessage(2.0, Set(latestMetricId, sumMetricId, avgMetricId, maxMetricId))

      // Post updates to each metric in order of 1 -> 3 -> 2
      //
      // For LATEST metric, result should be 1 -> 3 -> 2
      // For SUM metric, result should be 1 -> 4 -> 6
      // For AVERAGE metric, result should be 1 -> 2 -> 2
      // For MAX metric, result should be 1 -> 3 -> 3

      // Send update 1.0

      clock.reset(10)
      val t10 = clock.dateNow()
      Post("/smrt-base/health/updates", message1) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[HealthMetricUpdate]
        update.updateValue === 1.0
        update.timestamp === t10
      }
      Get(s"/smrt-base/health/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 1.0
        metric.severity === HealthSeverity.CAUTION
        metric.lastUpdate === Some(t10)
      }
      Get(s"/smrt-base/health/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 1.0
        metric.severity === HealthSeverity.CAUTION
        metric.lastUpdate === Some(t10)
      }
      Get(s"/smrt-base/health/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 1.0
        metric.severity === HealthSeverity.CAUTION
        metric.lastUpdate === Some(t10)
      }
      Get(s"/smrt-base/health/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 1.0
        metric.severity === HealthSeverity.CAUTION
        metric.lastUpdate === Some(t10)
      }

      // Send update 3.0

      clock.reset(20)
      val t20 = clock.dateNow()
      Post(s"/smrt-base/health/updates", message3) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[HealthMetricUpdate]
        update.updateValue === 3.0
        update.timestamp === t20
      }
      Get(s"/smrt-base/health/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 3.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t20)
      }
      Get(s"/smrt-base/health/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 4.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t20)
      }
      Get(s"/smrt-base/health/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t20)
      }
      Get(s"/smrt-base/health/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 3.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t20)
      }

      // Send update 2.0

      clock.reset(30)
      val t30 = clock.dateNow()
      Post(s"/smrt-base/health/updates", message2) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[HealthMetricUpdate]
        update.updateValue === 2.0
        update.timestamp === t30
      }
      Get(s"/smrt-base/health/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 6.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 3.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }

      clock.reset(1005)
      // All metrics have a window of 1 second, the first updates were at t = 10 ms, 1005 ms - 10 ms < 1000 ms, so
      // recalculating should not change the metric values

      Get(s"/smrt-base/health/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 6.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 3.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }

      clock.reset(1015)
      // Recalculating now should drop the first update at t = 10 ms
      // Note: this does not apply to the LATEST metric

      Get(s"/smrt-base/health/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 5.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.5
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 3.0
        metric.severity === HealthSeverity.CRITICAL
        metric.lastUpdate === Some(t30)
      }

      clock.reset(1025)
      // Recalculating now should drop the second update

      Get(s"/smrt-base/health/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }

      clock.reset(1035)
      val t1035 = clock.dateNow()
      // Recalculating now should drop the third update

      Get(s"/smrt-base/health/metrics/$latestMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 2.0
        metric.severity === HealthSeverity.ALERT
        metric.lastUpdate === Some(t30)
      }
      Get(s"/smrt-base/health/metrics/$sumMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 0.0
        metric.severity === HealthSeverity.OK
        metric.lastUpdate === None
      }
      Get(s"/smrt-base/health/metrics/$avgMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 0.0
        metric.severity === HealthSeverity.OK
        metric.lastUpdate === None
      }
      Get(s"/smrt-base/health/metrics/$maxMetricId") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metric = responseAs[HealthMetric]
        metric.metricValue === 0.0
        metric.severity === HealthSeverity.OK
        metric.lastUpdate === None
      }

      // Check the update records

      Get(s"/smrt-base/health/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[HealthMetricUpdate]]
        updates.size === 3
        updates(0).timestamp === t10
        updates(0).updateValue === 1.0
        updates(1).timestamp === t20
        updates(1).updateValue === 3.0
        updates(2).timestamp === t30
        updates(2).updateValue === 2.0
      }
      Get(s"/smrt-base/health/metrics/$latestMetricId/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[HealthMetricUpdate]]
        updates.size === 3
        updates(0).timestamp === t10
        updates(0).updateValue === 1.0
        updates(1).timestamp === t20
        updates(1).updateValue === 3.0
        updates(2).timestamp === t30
        updates(2).updateValue === 2.0
      }
      Get(s"/smrt-base/health/metrics/$sumMetricId/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[HealthMetricUpdate]]
        updates.size === 0
      }
      Get(s"/smrt-base/health/metrics/$avgMetricId/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[HealthMetricUpdate]]
        updates.size === 0
      }
      Get(s"/smrt-base/health/metrics/$maxMetricId/updates") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val updates = responseAs[Seq[HealthMetricUpdate]]
        updates.size === 0
      }
    }

    "return unhealthy messages" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, adminUserLogin)
      Post(s"/smrt-base/health/updates", HealthMetricUpdateMessage(1.0, Set(latestMetricId))) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[HealthMetricUpdate]
        update.updateValue === 1.0
      }
      Post(s"/smrt-base/health/updates", HealthMetricUpdateMessage(2.0, Set(sumMetricId))) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val update = responseAs[HealthMetricUpdate]
        update.updateValue === 2.0
      }

      Get("/smrt-base/health") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val metrics = responseAs[Seq[HealthMetric]]
        metrics.length === 2
        metrics.map(_.id) === Set(latestMetricId, sumMetricId)
        metrics.map(_.metricValue) === Set(1.0, 2.0)
      }
    }

    "reject unauthorized users" in new daoSetup {
      Get("/smrt-base/health/metrics") ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val invalid = RawHeader(JWT_HEADER, invalidJwt)
      Get("/smrt-base/health/metrics") ~> addHeader(invalid) ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val noAdmin = RawHeader(JWT_HEADER, readUserLogin)
      Post(s"/smrt-base/health/updates", HealthMetricUpdateMessage(0.0, Set.empty)) ~> addHeader(noAdmin) ~> routes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }
  }
}
