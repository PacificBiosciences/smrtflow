package com.pacbio.common.services.utils

import java.util.UUID

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{Constants, ServiceStatus}
import com.pacbio.common.time.{ClockProvider, Clock}
import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.{Duration => JodaDuration, Instant => JodaInstant, Period}

class StatusGenerator(clock: Clock,
                      baseServiceId: String,
                      uuid: UUID,
                      buildVersion: String) {

  val startedAt: JodaInstant = clock.now()

  private def uptimeMillis: Long = new JodaDuration(startedAt, clock.now()).getMillis

  private def uptimeString(uptimeMillis: Long): String = {
    val period = new Period(uptimeMillis)
    val formatter = new PeriodFormatterBuilder()
      .printZeroRarelyLast()
      .appendHours()
      .appendSuffix(" hour", " hours")
      .appendSeparator(", ", " and ")
      .appendMinutes()
      .appendSuffix(" minute", " minutes")
      .appendSeparator(", ", " and ")
      .appendSecondsWithOptionalMillis()
      .appendSuffix(" second", " seconds")
      .toFormatter
    period.toString(formatter)
  }

  def getStatus: ServiceStatus = {
    val up = uptimeMillis
    ServiceStatus(
      baseServiceId,
      s"Services have been up for ${uptimeString(up)}.",
      up,
      uuid,
      buildVersion,
      System.getenv("USER"))
  }
}

trait StatusGeneratorProvider {
  this: ClockProvider =>

  /**
   * Should be initialized at the top-level with
   * {{{override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)}}}
   */
  val buildPackage: Singleton[Package]

  /**
   * Should be initialized at the top-level with a base id for the total set of services. For instance, if you want your
   * service package to have id "pacbio.smrtservices.smrtlink_analysis", you would initialize this like so:
   * {{{override val baseServiceId: Singleton[String] = Singleton("smrtlink_analysis")}}}
   */
  val baseServiceId: Singleton[String]

  val uuid: Singleton[UUID] = Singleton(UUID.randomUUID())

  val buildVersion: Singleton[String] = Singleton(() => Constants.SMRTFLOW_VERSION)

  val statusGenerator: Singleton[StatusGenerator] =
    Singleton(() => new StatusGenerator(clock(), baseServiceId(), uuid(), buildVersion()))
}