package com.pacbio.common.database

import java.util.UUID

import com.pacbio.common.models._
import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import org.joda.time.{DateTime => JodaDateTime}

import slick.driver.SQLiteDriver.api._

object TableModels extends PacBioDateTimeDatabaseFormat {
  implicit def logLevelToString = MappedColumnType.base[LogLevel.LogLevel, String](
    logLevel => logLevel.toString,
    logLevelString => LogLevel.logLevelByName(logLevelString)
  )

  implicit def healthSeverityToString = MappedColumnType.base[HealthSeverity.HealthSeverity, String](
    healthSeverity => healthSeverity.toString,
    severityString => HealthSeverity.healthSeverityByName(severityString)
  )

  class LogResourceT(tag: Tag) extends Table[LogResource](tag, "LOG_RESOURCES") {
    def id: Rep[String] = column[String]("ID", O.PrimaryKey)

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def name: Rep[String] = column[String]("NAME")

    def description: Rep[String] = column[String]("DESCRIPTION")

    def * = (createdAt, description, id, name) <> (LogResource.tupled, LogResource.unapply)
  }

  case class LogMessageRow(resourceId: String, message: LogMessage)
  class LogMessageT(tag: Tag) extends Table[LogMessageRow](tag, "LOG_MESSAGES") {
    def resourceId: Rep[String] = column[String]("RESOURCE_ID")
    def resourceFk = foreignKey("RESOURCE_FK", resourceId, logResources)(_.id)

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def uuid: Rep[UUID] = column[UUID]("UUID")

    def message: Rep[String] = column[String]("MESSAGE")

    def level: Rep[LogLevel.LogLevel] = column[LogLevel.LogLevel]("SEVERITY")

    def sourceId: Rep[String] = column[String]("SOURCE_ID")

    def logMessage =
      (createdAt, uuid, message, level, sourceId) <> (LogMessage.tupled, LogMessage.unapply)
    def * = (resourceId, logMessage) <> (LogMessageRow.tupled, LogMessageRow.unapply)
  }

  case class HealthGaugeRow(createdAt: JodaDateTime, description: String, id: String, name: String)
  class HealthGaugeT(tag: Tag) extends Table[HealthGaugeRow](tag, "HEALTH_GAUGES") {
    def id: Rep[String] = column[String]("ID", O.PrimaryKey)

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def name: Rep[String] = column[String]("NAME")

    def description: Rep[String] = column[String]("DESCRIPTION")

    def * = (createdAt, description, id, name) <> (HealthGaugeRow.tupled, HealthGaugeRow.unapply)
  }

  case class HealthMessageRow(gaugeId: String, message: HealthGaugeMessage)
  class HealthMessageT(tag: Tag) extends Table[HealthMessageRow](tag, "HEALTH_GAUGE_MESSAGES") {
    def gaugeId: Rep[String] = column[String]("GAUGE_ID")
    def resourceFk = foreignKey("GAUGE_FK", gaugeId, healthGauges)(_.id)

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def uuid: Rep[UUID] = column[UUID]("UUID", O.PrimaryKey)

    def message: Rep[String] = column[String]("MESSAGE")

    def severity: Rep[HealthSeverity.HealthSeverity] = column[HealthSeverity.HealthSeverity]("SEVERITY")

    def sourceId: Rep[String] = column[String]("SOURCE_ID")

    def healthMessage =
      (createdAt, uuid, message, severity, sourceId) <> (HealthGaugeMessage.tupled, HealthGaugeMessage.unapply)
    def * = (gaugeId, healthMessage) <> (HealthMessageRow.tupled, HealthMessageRow.unapply)
  }

  lazy val logResources = TableQuery[LogResourceT]
  lazy val logMessages = TableQuery[LogMessageT]

  lazy val healthGauges = TableQuery[HealthGaugeT]
  lazy val healthMessages = TableQuery[HealthMessageT]
}
