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

    def uuid: Rep[UUID] = column[UUID]("UUID", O.PrimaryKey)

    def message: Rep[String] = column[String]("MESSAGE")

    def level: Rep[LogLevel.LogLevel] = column[LogLevel.LogLevel]("SEVERITY")

    def sourceId: Rep[String] = column[String]("SOURCE_ID")

    def logMessage =
      (createdAt, uuid, message, level, sourceId) <> (LogMessage.tupled, LogMessage.unapply)
    def * = (resourceId, logMessage) <> (LogMessageRow.tupled, LogMessageRow.unapply)
  }

  lazy val logResources = TableQuery[LogResourceT]
  lazy val logMessages = TableQuery[LogMessageT]
}
