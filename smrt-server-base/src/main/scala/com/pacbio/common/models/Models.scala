package com.pacbio.common.models

import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.duration._
import scala.language.postfixOps

object Models

object PacBioNamespaces {

  sealed trait PacBioNamespace { val name: String}
  // Commandline Tools, e.g., blasr
  case object SMRTTools extends PacBioNamespace { val name = "tools"}
  // Web Services, e.g., smrtlink_analysis
  case object SMRTServices extends PacBioNamespace {val name = "services"}
  // UI Applications, e.g., smrtlink_ui
  case object SMRTApps extends PacBioNamespace {val name = "apps"}

}

case class ThrowableResponse(httpCode: Int, message: String, errorType: String)

object MetricType {

  /**
   * Supertype for different types of health metrics
   */
  sealed trait MetricType

  /**
   * The value of the metric is the value of the latest update
   */
  case object LATEST extends MetricType

  /**
   * The value of the metric is the sum of the update values
   */
  case object SUM extends MetricType

  /**
   * The value of the metric is determined by a running average of the update values
   */
  case object AVERAGE extends MetricType

  /**
   * The value of the metric is the maximum update value seen
   */
  case object MAX extends MetricType

  val ALL = Seq(LATEST, SUM, AVERAGE, MAX)
}

object HealthSeverity {
  sealed class HealthSeverity(val severity: Int) extends Ordered[HealthSeverity] {
    def compare(s2: HealthSeverity): Int = severity compareTo s2.severity
  }

  case object OK extends HealthSeverity(severity = 0)
  case object CAUTION extends HealthSeverity(severity = 1)
  case object ALERT extends HealthSeverity(severity = 2)
  case object CRITICAL extends HealthSeverity(severity = 3)

  val ALL = Seq(OK, CAUTION, ALERT, CRITICAL)
  val healthSeverityByName = ALL.map(x => x.toString -> x).toMap
  val nameByHealthSeverity = ALL.map(x => x -> x.toString).toMap
}

object LogLevel {
  sealed abstract class LogLevel

  case object TRACE extends LogLevel
  case object DEBUG extends LogLevel
  case object INFO extends LogLevel
  case object NOTICE extends LogLevel
  case object WARN extends LogLevel
  case object ERROR extends LogLevel
  case object CRITICAL extends LogLevel
  case object FATAL extends LogLevel

  val ALL = Seq(TRACE, DEBUG, INFO, NOTICE, WARN, ERROR, CRITICAL, FATAL)
  val logLevelByName = ALL.map(x => x.toString -> x).toMap
}

// Subsystem Settings
case class SubsystemConfig(id: String, name: String, startedAt: JodaDateTime)

case class PacBioComponentManifest(id: String, name: String, version: String, description: String, dependencies: Seq[String] = Nil)

case class ServiceComponent(id: String, typeId: String, version: String)

// Not sure what this should be. Is this the subsystem config?
case class ServerConfig(id: String, version: String)

case class ServiceStatus(id: String, message: String, uptime: Long, uuid: UUID, version: String, user: String)


// Health System
case class TagCriteria(hasAny: Set[String] = Set.empty, hasAll: Set[String] = Set.empty, hasAnyPrefix: Set[String] = Set.empty, hasAllPrefix: Set[String] = Set.empty) {
  def matches(tags: Set[String]): Boolean = {
    hasAll.foreach { t => if (!tags.contains(t)) return false }
    hasAllPrefix.foreach { p => if (!tags.exists(_.startsWith(p))) return false }
    if ( !hasAny.forall { t => !tags(t) } ) return false
    if ( !hasAnyPrefix.forall { p => !tags.exists(_.startsWith(p)) } ) return false
    true
  }
}

case class HealthMetricCreateMessage(id: String, name: String, description: String, criteria: TagCriteria, metricType: MetricType.MetricType, severityLevels: Map[HealthSeverity.HealthSeverity, Double], windowSeconds: Option[Int])

case class HealthMetric(id: String, name: String, description: String, criteria: TagCriteria, metricType: MetricType.MetricType, severityLevels: Map[HealthSeverity.HealthSeverity, Double], windowSeconds: Option[Int], severity: HealthSeverity.HealthSeverity, metricValue: Double, createdAt: JodaDateTime, lastUpdate: Option[JodaDateTime])

case class HealthMetricUpdateMessage(updateValue: Double, tags: Set[String], note: Option[String] = None)

case class HealthMetricUpdate(updateValue: Double, tags: Set[String], note: Option[String], updateId: Long, timestamp: JodaDateTime)


// Logging System
case class LogResourceRecord(description: String, id: String, name: String)

case class LogResource(createdAt: JodaDateTime, description: String, id: String, name: String)

case class LogMessageRecord(message: String, level: LogLevel.LogLevel, sourceId: String)

case class LogMessage(createdAt: JodaDateTime, uuid: UUID, message: String, level: LogLevel.LogLevel, sourceId: String)


// Users

object Roles {
  sealed trait Role { val name: String }

  object PbAdmin extends Role { override val name = "Internal/PbAdmin" }
  object PbLabTech extends Role { override val name = "Internal/PbLabTech" }
  object PbBioinformatician extends Role { override val name = "Internal/PbBioinformatician" }

  val ALL_ROLES = Set(PbAdmin, PbLabTech, PbBioinformatician)

  def fromString(name: String): Option[Role] = ALL_ROLES.find(_.name == name)
}

case class UserRecord(userName: String,
                      firstName: Option[String] = None,
                      lastName: Option[String] = None,
                      roles: Set[Roles.Role] = Set.empty) {
  import Roles._

  /**
   * Determines whether the user is the given user. (Or has Admin privileges.)
   */
  def isUserOrAdmin(l: String): Boolean = userName.compareToIgnoreCase(l) == 0 || hasPermission(PbAdmin)

  /**
   * Determines whether the user has the given permissions.
   */
  def hasPermission(r: Role): Boolean = roles.contains(r)

  def getDisplayName: String = {
    val name = for {
      f <- firstName
      l <- lastName
    } yield s"$f $l"

    name.getOrElse(userName)
  }
}


// Config Service
case class ConfigEntry(key: String, value: String)

case class ConfigResponse(entries: Set[ConfigEntry], origin: String)


// Cleanup Service
object CleanupFrequency {
  sealed abstract class CleanupFrequency(
      period: FiniteDuration,
      tickRange: Int,
      tickDuration: FiniteDuration,
      getTick: JodaDateTime => Int) {
    def waitTime(now: JodaDateTime, at: Int) = {
      val nowTick = getTick(now)
      val waitTicks = if (at > nowTick) at - nowTick else tickRange + (at - nowTick)
      tickDuration * waitTicks
    }

    def getPeriod: FiniteDuration = period

    def getTickRange: Int = tickRange
  }

  case object HOURLY extends CleanupFrequency(1 hour, 60, 1 minute, _.getMinuteOfHour)
  case object DAILY extends CleanupFrequency(1 day, 24, 1 hour, _.getHourOfDay)
  case object WEEKLY extends CleanupFrequency(7 days, 7, 1 day, _.getDayOfWeek - 1)

  val ALL = Seq(HOURLY, DAILY, WEEKLY)
  val cleanupFrequencyByName = ALL.map(x => x.toString -> x).toMap
}

object CleanupSizeUnit {
  sealed abstract class CleanupSizeUnit(factor: Long) {
    def toBytes(size: Long): Long = size * factor
  }

  case object B extends CleanupSizeUnit(1)
  case object KB extends CleanupSizeUnit(1000)
  case object MB extends CleanupSizeUnit(1000 * 1000)
  case object GB extends CleanupSizeUnit(1000 * 1000 * 1000)

  val ALL = Seq(B, KB, MB, GB)
  val cleanupSizeUnitByName = ALL.map(x => x.toString -> x).toMap
}

object CleanupSize {
  def apply(stringForm: String): CleanupSize = {
    val subs = stringForm.trim.split("\\s+")
    if (subs.length != 2)
      throw new RuntimeException("Expected CleanupSize formatted as \"100 GB\"")
    else {
      val size = subs.head.toLong
      val unit = subs.last.toUpperCase
      if (!CleanupSizeUnit.cleanupSizeUnitByName.contains(unit))
        throw new RuntimeException(
          s"Expected CleanupSize unit to be one of ${CleanupSizeUnit.cleanupSizeUnitByName.keySet.toString()}")
      else
        new CleanupSize(size, CleanupSizeUnit.cleanupSizeUnitByName(unit))
    }
  }
}

class CleanupSize(size: Long, unit: CleanupSizeUnit.CleanupSizeUnit) {
  val bytes = unit.toBytes(size)
  override def toString: String = s"$size ${unit.toString}"
}

abstract class CleanupJobBase[T <: CleanupJobBase[T]] {
  // Note, these are not explicitly overriden in ApiCleanupJob and ConfigCleanupJob, but scala's case class magic
  // overrides them under the hood.
  def id: String
  def target: String
  def olderThan: Option[Duration]
  def minSize: Option[CleanupSize]
  def dryRun: Boolean
  def lastCheck: Option[JodaDateTime]
  def lastDelete: Option[JodaDateTime]

  def checked(t: JodaDateTime): T
  def deleted(t: JodaDateTime): T

  def scheduleString: String
  def toResponse: CleanupJobResponse =
    CleanupJobResponse(id, target, scheduleString, olderThan, minSize, dryRun, lastCheck, lastDelete)
}

case class CleanupJobResponse(
    id: String,
    target: String,
    schedule: String,
    olderThan: Option[Duration],
    minSize: Option[CleanupSize],
    dryRun: Boolean,
    lastCheck: Option[JodaDateTime],
    lastDelete: Option[JodaDateTime])

case class ApiCleanupJobCreate(
    target: String,
    frequency: CleanupFrequency.CleanupFrequency,
    at: Int,
    olderThan: Option[Duration],
    minSize: Option[CleanupSize],
    start: Option[Boolean],
    dryRun: Option[Boolean])

case class ApiCleanupJob(
    uuid: UUID,
    target: String,
    frequency: CleanupFrequency.CleanupFrequency,
    at: Int,
    olderThan: Option[Duration],
    minSize: Option[CleanupSize],
    running: Boolean,
    dryRun: Boolean,
    lastCheck: Option[JodaDateTime],
    lastDelete: Option[JodaDateTime])
  extends CleanupJobBase[ApiCleanupJob] {

  override def id: String = uuid.toString
  override def checked(t: JodaDateTime): ApiCleanupJob = copy(lastCheck = Some(t))
  override def deleted(t: JodaDateTime): ApiCleanupJob = copy(lastDelete = Some(t))
  override def scheduleString: String = s"$frequency at $at. ${if (running) "Running" else "Not running"}."
}

case class ConfigCleanupJobCreate(
    name: String,
    target: String,
    schedule: String,
    olderThan: Option[Duration],
    minSize: Option[CleanupSize],
    dryRun: Option[Boolean])

case class ConfigCleanupJob(
    name: String,
    target: String,
    schedule: String,
    olderThan: Option[Duration],
    minSize: Option[CleanupSize],
    dryRun: Boolean,
    lastCheck: Option[JodaDateTime],
    lastDelete: Option[JodaDateTime])
  extends CleanupJobBase[ConfigCleanupJob] {

  override def id: String = name
  override def checked(t: JodaDateTime): ConfigCleanupJob = copy(lastCheck = Some(t))
  override def deleted(t: JodaDateTime): ConfigCleanupJob = copy(lastDelete = Some(t))
  // TODO(smcclellan): Read quartz schedule from configs, or give filename/line number
  override def scheduleString: String = schedule
}

// Files Service
case class DirectoryResource(fullPath: String, subDirectories: Seq[DirectoryResource], files: Seq[FileResource])
case class FileResource(fullPath: String, name: String, mimeType: String, sizeInBytes: Long, sizeReadable: String)


// Disk Space Service
case class DiskSpaceResource(id: String, path: String, totalSpace: Long, usableSpace: Long, freeSpace: Long)

// SubSystem Resources
case class SubsystemResource(uuid: UUID, name: String, version: String, url: String, apiDocs: String, userDocs:String, createdAt: JodaDateTime, updatedAt: JodaDateTime)
// Record is what a user would POST
case class SubsystemResourceRecord(name: String, version: String, url: String, apiDocs: String, userDocs:String)
