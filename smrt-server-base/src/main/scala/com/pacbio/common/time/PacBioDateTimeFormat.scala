package com.pacbio.common.time

import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTimeZone => JodaDateTimeZone, DateTime => JodaDateTime}
import org.joda.time.format.{DateTimeFormatter => JodaDateTimeFormatter, ISODateTimeFormat}
import slick.driver.SQLiteDriver.api._
import scala.util.control.NonFatal

import com.pacbio.common.models.PacBioDateTimeFormat

/**
 * Implicit mapped column type that stores Joda DateTime objects as Longs (millis since epoch).
 *
 * Note: This is in contrast to the wire format (See {{{c.p.c.models.JodaDateTimeProtocol}}}),
 * which transmits DateTime objects as text. This is to protect the longevity of and shareability
 * of database records, as different clients and different versions may use different DateTime
 * formats. For the wire format, user-readability is more important than universality.
 *
 * Note: Because DateTime objects are stored as Longs, a client trying to reconstruct the
 * DateTime must supply a time zone. By using {{{PacBioDateTimeFormat.TIME_ZONE}}}, DateTime
 * objects that are read from a database will be reconstructed using the local time zone, as
 * defined by the system properties. This may result in confusion when testing, if different time
 * zones are used. For example, a timestamp like '2016-02-18T23:24:46.569Z', which is stored in a
 * database, and then reconstructed, may look like '2016-02-18T15:24:46.569-08:00'. These represent
 * the same absolute time, but with different timezones, because the original timezone was lost
 * during storage, and the object was reconstructed with the local, system property time zone. For
 * this reason, it is recommended that a {{{FakeClock}}} be used to generate all timestamps for
 * testing.
 */
trait PacBioDateTimeDatabaseFormat {
  import PacBioDateTimeFormat.TIME_ZONE

  implicit val jodaDateTimeType = MappedColumnType.base[JodaDateTime, Long](
    {d => d.getMillis} ,
    {d => new JodaDateTime(d, TIME_ZONE)}
  )
}
