package com.pacbio.common.alarms

import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.common.models.{AlarmUpdate, AlarmStatus, Alarm}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AlarmRunner {
  // Every implementation should define a globally unique alarm id.
  val alarm: Alarm

  // This should compute the alarm value: 0 good -> 1 bad.
  protected def update(): Future[AlarmUpdate]

  final def run(): Future[AlarmStatus] = update().map(u => AlarmStatus(alarm.id, u.value, u.message, u.severity, JodaDateTime.now()))
}