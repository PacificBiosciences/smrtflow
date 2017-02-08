package com.pacbio.common.actors

import com.pacbio.common.alarms.{AlarmComposer, AlarmRunner}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/**
 * DAO interface for the alarm metric system.
 */
trait AlarmDao {
  /**
   * Returns a list of all alarms
   */
  def getAlarms: Future[Seq[Alarm]]

  /**
   * Computes and returns a list of all alarm statuses
   */
  def getAlarmStatuses: Future[Seq[AlarmStatus]]

  /**
   * Returns an alarm by id
   */
  def getAlarm(id: String): Future[Alarm]

  /**
   * Computes and returns an alarm status by id
   */
  def getAlarmStatus(id: String): Future[AlarmStatus]

  /**
   * Returns a text summary of all alarms. For debugging purposes.
   */
  def getSummary: Future[String]
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

class InMemoryAlarmDao(runners: Seq[AlarmRunner]) extends AlarmDao {
  private def findRunner(id: String): Future[AlarmRunner] = Future {
    runners.find(_.alarm.id == id).getOrElse(throw new ResourceNotFoundError(s"No alrm with id $id found"))
  }

  override def getAlarms: Future[Seq[Alarm]] = Future { runners.map(_.alarm) }

  override def getAlarmStatuses: Future[Seq[AlarmStatus]] = Future.sequence(runners.map(_.run()))

  override def getAlarm(id: String): Future[Alarm] = findRunner(id).map(_.alarm)

  override def getAlarmStatus(id: String): Future[AlarmStatus] = findRunner(id).flatMap(_.run())

  override def getSummary: Future[String] = getAlarmStatuses.map { a =>
    val summary = a.map(s => s"${s.id} -> ${s.message}").reduceLeftOption(_ + "\n" + _).getOrElse("No Alarms")
    s"Alarm Summary\n$summary"
  }
}

/**
 * Provides an InMemoryAlarmMetricDao.
 */
trait InMemoryAlarmDaoProvider extends AlarmDaoProvider {
  this: AlarmComposer =>

  override val alarmDao: Singleton[AlarmDao] = Singleton(() => new InMemoryAlarmDao(alarms()))
}