package com.pacbio.secondary.smrtlink.alarms

import com.pacbio.secondary.smrtlink.models.{
  Alarm,
  AlarmSeverity,
  AlarmStatus,
  AlarmUpdate
}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

trait AlarmRunner extends LazyLogging {
  // Every implementation should define a globally unique alarm id.
  val alarm: Alarm

  // This should compute the alarm value: 0 good -> 1 bad.
  protected def update(): Future[AlarmUpdate]

  final def run(): Future[AlarmStatus] =
    update()
      .map(
        u =>
          AlarmStatus(alarm.id,
                      u.value,
                      u.message,
                      u.severity,
                      JodaDateTime.now()))
      .recover {
        case NonFatal(ex) =>
          val errorMessage =
            s"Failed to compute ${alarm.name} Error ${ex.getMessage}"
          logger.error(errorMessage)
          AlarmStatus(alarm.id,
                      1.0,
                      Some(errorMessage),
                      AlarmSeverity.ERROR,
                      JodaDateTime.now())
      }
}
