package com.pacbio.common.actors

import java.util.concurrent.atomic.AtomicLong

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors
import com.pacbio.common.time.{ClockProvider, Clock}
import org.joda.time.{DateTime => JodaDateTime}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

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
      .map { ws => updates.filter(_.timestamp.plusSeconds(ws).isAfter(now)) }.getOrElse(updates)
      .filter { u => u.timestamp.isBefore(now) || u.timestamp.isEqual(now) }
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
    val newLastUpdate = if (updates.isEmpty) metric.lastUpdate else Some(updates.map(_.timestamp).maxBy(_.getMillis))
    val newMetric = metric.copy(metricValue = newValue, severity = newSeverity, lastUpdate = newLastUpdate)
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
          lastUpdate = None)
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