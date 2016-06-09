package com.pacbio.common.actors

import java.sql.SQLException
import java.util.concurrent.atomic.AtomicLong

import akka.pattern.AskTimeoutException
import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.{InitializationComposer, RequiresInitialization, Singleton}
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors
import com.pacbio.common.time.{ClockProvider, Clock}
import org.joda.time.{DateTime => JodaDateTime}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
 * DAO interface for the health metric system.
 */
trait HealthDao {
  /**
   * Returns a seq of all current health metrics.
   */
  def getAllHealthMetrics: Future[Seq[HealthMetric]]

  /**
   * Returns a health metric by id.
   */
  def getHealthMetric(id: String): Future[HealthMetric]

  /**
   * Creates a new health metric.
   */
  def createHealthMetric(m: HealthMetricCreateMessage): Future[HealthMetric]

  /**
   * Returns a seq of all updates to the given metric.
   */
  def getMetricUpdates(id: String): Future[Seq[HealthMetricUpdate]]

  /**
   * Returns a seq of all updates.
   */
  def getAllUpdates: Future[Seq[HealthMetricUpdate]]

  /**
   * Posts an update.
   */
  def update(m: HealthMetricUpdateMessage): Future[HealthMetricUpdate]

  /**
   * Returns a seq of all metrics that are not in the OK state.
   */
  def getUnhealthyMetrics: Future[Seq[HealthMetric]]
}

/**
 * Provider for injecting a singleton HealthMetricDao. Concrete providers must override the healthMetricDao val.
 */
trait HealthDaoProvider {
  /**
   * Singleton Health Metric DAO object.
   */
  val healthDao: Singleton[HealthDao]
}

class InMemoryHealthDao(clock: Clock) extends HealthDao {

  import HealthSeverity._
  import PacBioServiceErrors._

  val nextUpdateId: AtomicLong = new AtomicLong(0)
  val metrics: mutable.Map[String, HealthMetric] = new mutable.HashMap
  var updates: Vector[HealthMetricUpdate] = Vector()

  private def searchUpdates(metric: HealthMetric, now: JodaDateTime): Seq[HealthMetricUpdate] =
    metric.windowSeconds
      .map { ws => updates.filter(_.updatedAt.plusSeconds(ws).isAfter(now)) }.getOrElse(updates)
      .filter { u => u.updatedAt.isBefore(now) || u.updatedAt.isEqual(now) }
      .filter { u => metric.criteria.matches(u.tags) }

  private def severityByValue(severityLevels: Map[HealthSeverity, Double], value: Double): HealthSeverity = {
    val l = severityLevels.filter(_._2 <= value)
    if (l.isEmpty) OK else l.maxBy(_._2)._1
  }

  private def recalculate(metric: HealthMetric, now: JodaDateTime): Future[HealthMetric] = Future {
    import MetricType._

    val updates = searchUpdates(metric, now)

    val newValue: Double = metric.metricType match {
      case LATEST => updates.lastOption.map(_.updateValue).getOrElse(0.0)
      case SUM => updates.map(_.updateValue).sum
      case AVERAGE => if (updates.isEmpty) 0.0 else updates.map(_.updateValue).sum / updates.size
      case MAX => if (updates.isEmpty) 0.0 else updates.map(_.updateValue).max
    }
    val newSeverity = severityByValue(metric.severityLevels, newValue)
    val newMetric = metric.copy(metricValue = newValue, severity = newSeverity, updatedAt = Some(now))
    metrics(metric.id) = newMetric
    newMetric
  }

  override def getAllHealthMetrics: Future[Seq[HealthMetric]] = {
    val now = clock.dateNow()
    Future.sequence(metrics.values.map(recalculate(_, now)).toSeq)
  }

  override def getHealthMetric(id: String): Future[HealthMetric] =
    recalculate(metrics.getOrElse(id, throw new ResourceNotFoundError(s"Unable to find metric $id")), clock.dateNow())

  override def createHealthMetric(m: HealthMetricCreateMessage): Future[HealthMetric] = Future {
    metrics.synchronized {
      if (metrics contains m.id)
        throw new UnprocessableEntityError(s"Metric with id ${m.id} already exists")
      else {
        val metric = HealthMetric(
          m.id,
          m.name,
          m.description,
          m.criteria,
          m.metricType,
          m.severityLevels - OK,
          if (m.metricType == MetricType.LATEST) None else m.windowSeconds,
          HealthSeverity.OK,
          0.0,
          clock.dateNow(),
          updatedAt = None)
        metrics(m.id) = metric
        metric
      }
    }
  }

  override def getMetricUpdates(id: String): Future[Seq[HealthMetricUpdate]] = Future {
    val metric = metrics.getOrElse(id, throw new ResourceNotFoundError(s"Unable to find metric $id"))
    searchUpdates(metric, clock.dateNow())
  }

  override def getAllUpdates: Future[Seq[HealthMetricUpdate]] = Future(updates.toSeq)

  override def update(m: HealthMetricUpdateMessage): Future[HealthMetricUpdate] = Future {
    nextUpdateId.synchronized {
      val updatedAt = clock.dateNow()
      val update = HealthMetricUpdate(
        m.updateValue,
        m.tags,
        m.note,
        nextUpdateId.getAndIncrement(),
        updatedAt
      )
      updates = updates :+ update
      update
    }
  }

  override def getUnhealthyMetrics: Future[Seq[HealthMetric]] =
    getAllHealthMetrics.map(_.filter(_.severity > OK))

  @VisibleForTesting
  def clear(): Unit = {
    metrics.synchronized {
      nextUpdateId.synchronized {
        metrics.clear()
        updates = Vector()
        nextUpdateId.set(0)
      }
    }
  }
}

/**
 * Provides an InMemoryHealthMetricDao.
 */
trait InMemoryHealthDaoProvider extends HealthDaoProvider {
  this: ClockProvider =>

  override val healthDao: Singleton[HealthDao] = Singleton(() => new InMemoryHealthDao(clock()))
}

// Right now, this should only be used to track metrics on asks to akka actors wrapping db interactions. E.g.:
//
// val metrics: Metrics
// metrics(DoSomething ? dbActor)
// TODO(smcclellan): Move this to its own file
// TODO(smcclellan): Make the name specific to fit the function, or make the function general to fit the name.
class Metrics(healthDao: HealthDao,
              sysClock: Clock,
              tags: Set[String] = Set(),
              windowSeconds: Option[Int] = Some(300)) extends RequiresInitialization {

  val LATENCY = "latency"
  val FAILURE_RATE = "failure-rate"
  val ASK_TIMEOUT_RATE = "ask-timeout-rate"
  val SQL_ERROR_RATE = "sql-error-rate"

  def metricId(tag: Option[String], suffix: String): String = {
    tag.foreach(t => require(tags contains t, s"Unrecognized metric tag $t"))
    tag.map(t => s"${t.toLowerCase}-$suffix").getOrElse(suffix)
  }

  override def init(): Set[HealthMetric] = {
    import HealthSeverity._
    import MetricType._

    val tagOptions = new mutable.ArrayBuffer[Option[String]]
    tagOptions += None
    tagOptions ++= tags.map(Some(_))

    tagOptions.flatMap { tag =>
      val latency = HealthMetricCreateMessage(
        metricId(tag, LATENCY),
        s"Latency Metric: ${tag.getOrElse("default")}",
        s"Latency Metric: ${tag.getOrElse("default")}",
        TagCriteria(hasAll = Set(LATENCY)),
        AVERAGE, // TODO(smcclellan): Use MAX?
        Map(CAUTION -> 1000.0, ALERT -> 1500.0, CRITICAL -> 2000.0), // TODO(smcclellan): This should be configurable
        windowSeconds
      )

      val failureRate = HealthMetricCreateMessage(
        metricId(tag, FAILURE_RATE),
        s"Failure Rate Metric: ${tag.getOrElse("default")}",
        s"Failure Rate Metric: ${tag.getOrElse("default")}",
        TagCriteria(hasAll = Set(FAILURE_RATE)),
        AVERAGE,
        Map(CAUTION -> 0.1, ALERT -> 0.25, CRITICAL -> 0.5),
        windowSeconds
      )

      val timeoutRate = HealthMetricCreateMessage(
        metricId(tag, ASK_TIMEOUT_RATE),
        s"Timeout Rate Metric: ${tag.getOrElse("default")}",
        s"Timeout Rate Metric: ${tag.getOrElse("default")}",
        TagCriteria(hasAll = Set(ASK_TIMEOUT_RATE)),
        AVERAGE,
        Map(CAUTION -> 0.05, ALERT -> 0.1, CRITICAL -> 0.25),
        windowSeconds
      )

      val sqlErrorRate = HealthMetricCreateMessage(
        metricId(tag, SQL_ERROR_RATE),
        s"SQL Error Rate Metric: ${tag.getOrElse("default")}",
        s"SQL Error Rate Metric: ${tag.getOrElse("default")}",
        TagCriteria(hasAll = Set(SQL_ERROR_RATE)),
        AVERAGE,
        Map(CAUTION -> 0.05, ALERT -> 0.1, CRITICAL -> 0.25),
        windowSeconds
      )

      Seq(
        Await.result(healthDao.createHealthMetric(latency), 1.second),
        Await.result(healthDao.createHealthMetric(failureRate), 1.second),
        Await.result(healthDao.createHealthMetric(timeoutRate), 1.second),
        Await.result(healthDao.createHealthMetric(sqlErrorRate), 1.second)
      )
    }.toSet
  }

  def apply[T](future: => Future[T], tag: Option[String] = None): Future[T] = {

    def getNote(t: Throwable): Option[String] = Some {
      import java.io.{PrintWriter, StringWriter}

      val sw = new StringWriter()
      val pw = new PrintWriter(sw)
      t.printStackTrace(pw)
      sw.toString
    }

    val t1 = sysClock.now()
    future.andThen {
      case Success(_) =>
        val t2 = sysClock.now()
        healthDao.update(HealthMetricUpdateMessage(t2.getMillis - t1.getMillis, Set(LATENCY)))
        healthDao.update(HealthMetricUpdateMessage(0.0, Set(FAILURE_RATE, ASK_TIMEOUT_RATE, SQL_ERROR_RATE)))
      case Failure(t) if t.isInstanceOf[AskTimeoutException] =>
        healthDao.update(HealthMetricUpdateMessage(1.0, Set(FAILURE_RATE, ASK_TIMEOUT_RATE), getNote(t)))
      case Failure(t) if t.isInstanceOf[SQLException] =>
        healthDao.update(HealthMetricUpdateMessage(1.0, Set(FAILURE_RATE, SQL_ERROR_RATE), getNote(t)))
      case Failure(t) =>
        healthDao.update(HealthMetricUpdateMessage(1.0, Set(FAILURE_RATE), getNote(t)))
    }
  }
}

trait MetricsProvider {
  this: InitializationComposer with ClockProvider with HealthDaoProvider =>

  val metrics: Singleton[Metrics] =
    requireInitialization(Singleton(() => new Metrics(healthDao(), clock())))
}