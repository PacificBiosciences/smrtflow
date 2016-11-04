
package com.pacbio.common.models

import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTimeZone => JodaDateTimeZone, DateTime => JodaDateTime}
import org.joda.time.format.{DateTimeFormatter => JodaDateTimeFormatter, ISODateTimeFormat}
import scala.util.control.NonFatal

/**
 * Provides standardized (ISO) formats for dates, times, and datetimes. All formats use the local
 * time zone, as provided by the system properties.
 */
object PacBioDateTimeFormat {
  // TODO(smcclellan): Consider removing all uses of local time zones, and use UTC everywhere?
  lazy val TIME_ZONE: JodaDateTimeZone = {
    val configPath = "user.timezone"
    val defaultZone: JodaDateTimeZone = JodaDateTimeZone.UTC

    val conf = ConfigFactory.load()
    if (conf.hasPath(configPath))
      try {
        JodaDateTimeZone.forID(conf.getString(configPath))
      } catch {
        case NonFatal(_) => defaultZone
      }
    else defaultZone
  }

  val DATE_TIME_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.dateTime().withZone(TIME_ZONE)
  val DATE_TIME_NO_MILLIS_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis().withZone(TIME_ZONE)
  val DATE_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.date().withZone(TIME_ZONE)
  val TIME_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.time().withZone(TIME_ZONE)
  val TIME_NO_MILLIS_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.timeNoMillis().withZone(TIME_ZONE)
}
