package com.pacbio.common.database

import java.util.UUID

import com.pacbio.common.models.{LogMessage, LogLevel}
import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import org.joda.time.{DateTime => JodaDateTime}

import scala.slick.driver.SQLiteDriver.simple._

object LogDatabaseSchema extends PacBioDateTimeDatabaseFormat {
  // Define serialization/deserialization of LogLevel for database storage
  implicit def logLevelToString = MappedColumnType.base[LogLevel.LogLevel, String](
    logLevel => logLevel.toString,
    logLevelString => LogLevel.logLevelByName(logLevelString)
  )

  // LogMessageTable schema
  case class LogMessageRow(id: String, message: LogMessage)
  class LogMessageTable(tag: Tag) extends Table[LogMessageRow](tag, "LOG_MESSAGE") {
    def id = column[String]("ID")
    def createdAt = column[JodaDateTime]("CREATED_AT")
    def uuid = column[UUID]("UUID")
    def message = column[String]("MESSAGE")
    def level = column[LogLevel.LogLevel]("SEVERITY")
    def sourceId = column[String]("SOURCE_ID")

    // def key = primaryKey("KEY", (id, uuid))

    def logMessage =
      (createdAt, uuid, message, level, sourceId) <> (LogMessage.tupled, LogMessage.unapply)
    def * = (id, logMessage) <> (LogMessageRow.tupled, LogMessageRow.unapply)
  }

  val logMessageTable = TableQuery[LogMessageTable]
}
