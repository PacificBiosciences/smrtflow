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
  def getAllMetricUpdates(id: String): Future[Seq[HealthMetricUpdate]]

  /**
   * Updates a metric.
   */
  def updateMetric(m: HealthMetricUpdateMessage): Future[HealthMetricUpdate]

  /**
   * Returns a seq of all metrics that are not in the OK state.
   */
  def getUnhealthyMetrics: Future[Seq[HealthMetric]]

  /**
   * Recalculates the values of all metrics. (This should be called regularly, as time windows shift, stale updates must
   * be dropped.)
   */
  def recalculate: Future[Unit]
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
  val updates: mutable.Map[String, mutable.Buffer[HealthMetricUpdate]] = new mutable.HashMap
  val windows: mutable.Map[String, mutable.Buffer[HealthMetricUpdate]] = new mutable.HashMap

  private def getWindow(id: String, now: JodaDateTime): mutable.Buffer[HealthMetricUpdate] = {
    if (windows contains id) {
      val windowSeconds = metrics(id).windowSeconds.get
      windows(id) = windows(id).filter(_.updatedAt.plusSeconds(windowSeconds).isAfter(now))
      windows(id)
    } else updates(id)
  }

  private def severityByValue(severityLevels: Map[Double, HealthSeverity], value: Double): HealthSeverity =
    severityLevels.filter(_._1 > value).maxBy(_._1)._2

  override def getAllHealthMetrics: Future[Seq[HealthMetric]] = Future(metrics.values.toSeq)

  override def getHealthMetric(id: String): Future[HealthMetric] =
    Future(metrics.getOrElse(id, throw new ResourceNotFoundError(s"Unable to find metric $id")))

  override def createHealthMetric(m: HealthMetricCreateMessage): Future[HealthMetric] = Future {
    metrics.synchronized {
      if (metrics contains m.id)
        throw new UnprocessableEntityError(s"Metric with id ${m.id} already exists")
      else {
        val metric = HealthMetric(
          m.id,
          m.name,
          m.description,
          m.metricType,
          m.severityLevels,
          if (m.metricType == MetricType.LATEST) Some(0) else m.windowSeconds,
          HealthSeverity.OK,
          0.0,
          clock.dateNow(),
          updatedAt = None)
        metrics(m.id) = metric
        updates(m.id) = new mutable.ListBuffer
        if (m.windowSeconds.isDefined) windows(m.id) = new mutable.ListBuffer
        metric
      }
    }
  }

  override def getAllMetricUpdates(id: String): Future[Seq[HealthMetricUpdate]] =
    Future(updates.getOrElse(id, throw new ResourceNotFoundError(s"Unable to find metric $id")))

  override def updateMetric(m: HealthMetricUpdateMessage): Future[HealthMetricUpdate] = Future {
    import MetricType._

    if (updates contains m.id) {
      updates(m.id).synchronized {
        val updatedAt = clock.dateNow()
        val metric = metrics(m.id)

        val newValue: Double = metric.metricType match {
          case LATEST => m.updateValue
          case SUM => getWindow(m.id, updatedAt).map(_.updateValue).sum + m.updateValue
          case AVERAGE =>
            val ups = getWindow(m.id, updatedAt).map(_.updateValue)
            (ups.sum + m.updateValue) / (ups.size + 1)
          case MAX =>
            val ups = getWindow(m.id, updatedAt).map(_.updateValue)
            if (ups.isEmpty) m.updateValue else ups.max.max(m.updateValue)
        }

        val newSeverity = severityByValue(metric.severityLevels, newValue)
        val update = HealthMetricUpdate(
          m.id,
          m.updateValue,
          nextUpdateId.getAndIncrement(),
          newValue,
          newSeverity,
          updatedAt)

        updates(m.id) += update
        if (windows contains m.id) windows(m.id) += update
        metrics(m.id) = metrics(m.id).copy(metricValue = newValue, severity = newSeverity, updatedAt = Some(updatedAt))
        update
      }
    } else throw new ResourceNotFoundError(s"Unable to find metric ${m.id}")
  }

  override def getUnhealthyMetrics: Future[Seq[HealthMetric]] =
    Future(metrics.values.filter(_.severity != HealthSeverity.OK).toSeq)

  override def recalculate: Future[Unit] = Future {
    import MetricType._

    metrics.transform { (id, m) =>
      updates(id).synchronized {
        val now = clock.dateNow()
        val newValue: Double = m.metricType match {
          case LATEST => m.metricValue
          case SUM => getWindow(id, now).map(_.updateValue).sum
          case AVERAGE =>
            val ups = getWindow(id, now).map(_.updateValue)
            if(ups.isEmpty) 0.0 else ups.sum / ups.size
          case MAX =>
            val ups = getWindow(id, now).map(_.updateValue)
            if (ups.isEmpty) 0.0 else ups.max
        }
        val updatedAt = if (m.metricValue == newValue) m.updatedAt else Some(now)
        val newSeverity = severityByValue(m.severityLevels, newValue)
        m.copy(metricValue = newValue, updatedAt = updatedAt, severity = newSeverity)
      }
    }
  }

  @VisibleForTesting
  def clear(): Unit = {
    metrics.synchronized {
      for (id <- metrics.keys) {
        updates(id).synchronized {
          metrics -= id
          updates -= id
          windows -= id
        }
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