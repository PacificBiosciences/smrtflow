package com.pacbio.common.services

import java.util.{Properties, UUID}

import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.time.{ClockProvider, Clock}
import org.joda.time.{Instant => JodaInstant, Duration => JodaDuration, Period}
import org.joda.time.format.PeriodFormatterBuilder
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.duration._

class StatusService(clock: Clock, statusGenerator: StatusGenerator) extends PacBioService {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("status"),
    "Status Service",
    "0.2.0", "Subsystem Status Service")

  val statusServiceName = "status"

  val routes =
    path(statusServiceName) {
      get {
        complete {
          ok {
            statusGenerator.status
          }
        }
      }
    }
}

class StatusGenerator(clock: Clock, baseServiceId: String, uuid: UUID, buildVersion: String) {
  val startedAt: JodaInstant = clock.now()

  def uptimeMillis: Long = new JodaDuration(startedAt, clock.now()).getMillis

  def uptimeString(uptimeMillis: Long): String = {
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

  def status: ServiceStatus = {
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

  val buildVersion: Singleton[String] = Singleton(() => {
    val files = getClass.getClassLoader.getResources("version.properties")
    if (files.hasMoreElements) {
      val in = files.nextElement().openStream()
      try {
        val prop = new Properties
        prop.load(in)
        prop.getProperty("version").replace("SNAPSHOT", "") + prop.getProperty("sha1").substring(0, 7)
      }
      finally {
        in.close()
      }
    }
    else {
      "unknown version"
    }
  })

  val statusGenerator: Singleton[StatusGenerator] =
    Singleton(() => new StatusGenerator(clock(), baseServiceId(), uuid(), buildVersion()))
}

/**
 * Provides a singleton StatusService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{StatusServiceActorRefProvider}}}.
 */
trait StatusServiceProvider {
  this: ClockProvider with StatusGeneratorProvider =>

  val statusService: Singleton[StatusService] =
    Singleton(() => new StatusService(clock(), statusGenerator())).bindToSet(AllServices)
}

trait StatusServiceProviderx {
  this: ClockProvider with StatusGeneratorProvider with ServiceComposer =>

  val statusService: Singleton[StatusService] = Singleton(() => new StatusService(clock(), statusGenerator()))

  addService(statusService)
}
