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
 * DAO interface for the alarm metric system.
 */
trait AlarmDao {
  /**
   * Returns a seq of all current alarm metrics.
   */
  def getAllAlarmMetrics: Future[Seq[AlarmMetric]]

  /**
   * Returns a alarm metric by id.
   */
  def getAlarmMetric(id: String): Future[AlarmMetric]

  /**
   * Creates a new alarm metric.
   */
  def createAlarmMetric(m: AlarmMetricCreateMessage): Future[AlarmMetric]

  /**
   * Returns a seq of all updates to the given metric.
   */
  def getMetricUpdates(id: String): Future[Seq[AlarmMetricUpdate]]

  /**
   * Returns a seq of all updates.
   */
  def getAllUpdates: Future[Seq[AlarmMetricUpdate]]

  /**
   * Posts an update.
   */
  def update(m: AlarmMetricUpdateMessage): Future[AlarmMetricUpdate]

  /**
   * Returns a seq of all metrics that are not in the OK state.
   */
  def getUnhealthyMetrics: Future[Seq[AlarmMetric]]
}

/**
 * Provider for injecting a singleton AlarmMetricDao. Concrete providers must override the healthMetricDao val.
 */
trait AlarmDaoProvider {
  /**
   * Singleton Alarm Metric DAO object.
   */
  val alarmDao: Singleton[AlarmDao]
}

class InMemoryAlarmDao(clock: Clock) extends AlarmDao {

  import AlarmSeverity._
  import PacBioServiceErrors._

  val nextUpdateId: AtomicLong = new AtomicLong(0)
  val metrics: mutable.Map[String, AlarmMetric] = new mutable.HashMap
  var updates: Vector[AlarmMetricUpdate] = Vector()

  private def searchUpdates(metric: AlarmMetric, now: JodaDateTime): Seq[AlarmMetricUpdate] =
    metric.windowSeconds
      .map { ws => updates.filter(_.timestamp.plusSeconds(ws).isAfter(now)) }.getOrElse(updates)
      .filter { u => u.timestamp.isBefore(now) || u.timestamp.isEqual(now) }
      .filter { u => metric.criteria.matches(u.tags) }

  private def severityByValue(severityLevels: Map[AlarmSeverity, Double], value: Double): AlarmSeverity = {
    val l = severityLevels.filter(_._2 <= value)
    if (l.isEmpty) CLEAR else l.maxBy(_._2)._1
  }

  private def recalculate(metric: AlarmMetric, now: JodaDateTime): Future[AlarmMetric] = Future {
    import MetricType._

    val updates = searchUpdates(metric, now)

    val newValue: Double = metric.metricType match {
      case LATEST => updates.lastOption.map(_.updateValue).getOrElse(0.0)
      case SUM => updates.map(_.updateValue).sum
      case AVERAGE => if (updates.isEmpty) 0.0 else updates.map(_.updateValue).sum / updates.size
      case MAX => if (updates.isEmpty) 0.0 else updates.map(_.updateValue).max
    }
    val newSeverity = severityByValue(metric.severityLevels, newValue)
    val newLastUpdate = if (updates.isEmpty) None else Some(updates.map(_.timestamp).maxBy(_.getMillis))
    val newMetric = metric.copy(metricValue = newValue, severity = newSeverity, lastUpdate = newLastUpdate)
    metrics(metric.id) = newMetric
    newMetric
  }

  override def getAllAlarmMetrics: Future[Seq[AlarmMetric]] = {
    val now = clock.dateNow()
    Future.sequence(metrics.values.map(recalculate(_, now)).toSeq)
  }

  override def getAlarmMetric(id: String): Future[AlarmMetric] =
    recalculate(metrics.getOrElse(id, throw new ResourceNotFoundError(s"Unable to find metric $id")), clock.dateNow())


  override def createAlarmMetric(m: AlarmMetricCreateMessage): Future[AlarmMetric] = Future {
    metrics.synchronized {
      if (metrics contains m.id)
        throw new UnprocessableEntityError(s"Metric with id ${m.id} already exists")
      else {
        val metric = AlarmMetric(
          m.id,
          m.name,
          m.description,
          m.criteria,
          m.metricType,
          m.severityLevels - CLEAR,
          m.windowSeconds,
          AlarmSeverity.CLEAR,
          0.0,
          clock.dateNow(),
          lastUpdate = None)
        metrics(m.id) = metric
        metric
      }
    }
  }

  override def getMetricUpdates(id: String): Future[Seq[AlarmMetricUpdate]] = Future {
    val metric = metrics.getOrElse(id, throw new ResourceNotFoundError(s"Unable to find metric $id"))
    searchUpdates(metric, clock.dateNow())
  }

  override def getAllUpdates: Future[Seq[AlarmMetricUpdate]] = Future(updates.toSeq)

  override def update(m: AlarmMetricUpdateMessage): Future[AlarmMetricUpdate] = Future {
    nextUpdateId.synchronized {
      val updatedAt = clock.dateNow()
      val update = AlarmMetricUpdate(
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

  override def getUnhealthyMetrics: Future[Seq[AlarmMetric]] =
    getAllAlarmMetrics.map(_.filter(_.severity > CLEAR))

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
 * Provides an InMemoryAlarmMetricDao.
 */
trait InMemoryAlarmDaoProvider extends AlarmDaoProvider {
  this: ClockProvider =>

  override val alarmDao: Singleton[AlarmDao] = Singleton(() => new InMemoryAlarmDao(clock()))
}