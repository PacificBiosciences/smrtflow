package com.pacbio.secondary.smrtlink.time

import java.sql.Timestamp

import org.joda.time.{DateTimeZone => JodaDateTimeZone, DateTime => JodaDateTime}
import org.joda.time.format.{DateTimeFormatter => JodaDateTimeFormatter, ISODateTimeFormat}
import slick.driver.PostgresDriver.api._

/**
 * Provides standardized (ISO) formats for dates, times, and datetimes. All formats use the local
 * time zone, as provided by the system properties.
 */
object PacBioDateTimeFormat {
  val TIME_ZONE: JodaDateTimeZone =  JodaDateTimeZone.UTC
  val DATE_TIME_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.dateTime().withZone(TIME_ZONE)
  val DATE_TIME_NO_MILLIS_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis().withZone(TIME_ZONE)
  val DATE_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.date().withZone(TIME_ZONE)
  val TIME_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.time().withZone(TIME_ZONE)
  val TIME_NO_MILLIS_FORMAT: JodaDateTimeFormatter = ISODateTimeFormat.timeNoMillis().withZone(TIME_ZONE)
}

trait PacBioDateTimeDatabaseFormat {
  import PacBioDateTimeFormat.TIME_ZONE

  implicit val jodaDateTimeType = MappedColumnType.base[JodaDateTime, Timestamp](
    {d => new Timestamp(d.getMillis)} ,
    {d => new JodaDateTime(d, TIME_ZONE)}
  )
}
