package com.pacbio.common.database

import java.util.UUID

import com.pacbio.common.models.{HealthGaugeMessage, HealthSeverity}
import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import org.joda.time.{DateTime => JodaDateTime}

import scala.slick.driver.SQLiteDriver.simple._

object HealthDatabaseSchema extends PacBioDateTimeDatabaseFormat {
  // Define serialization/deserialization of HealthSeverity for database storage
  implicit def healthSeverityToString = MappedColumnType.base[HealthSeverity.HealthSeverity, String](
    healthSeverity => healthSeverity.toString,
    severityString => HealthSeverity.healthSeverityByName(severityString)
  )

  // HealthGaugeMessageTable schema
  case class HealthGaugeMessageRow(id: String, message: HealthGaugeMessage)
  class HealthGaugeMessageTable(tag: Tag)
    extends Table[HealthGaugeMessageRow](tag, "HEALTH_GAUGE_MESSAGE") {
    def id = column[String]("ID")
    def createdAt = column[JodaDateTime]("CREATED_AT")
    def uuid = column[UUID]("UUID" /*, O.PrimaryKey */)
    def message = column[String]("MESSAGE")
    def severity = column[HealthSeverity.HealthSeverity]("SEVERITY")
    def sourceId = column[String]("SOURCE_ID")

    def healthGaugeMessage =
      (createdAt, uuid, message, severity, sourceId) <> (HealthGaugeMessage.tupled, HealthGaugeMessage.unapply)
    def * = (id, healthGaugeMessage) <> (HealthGaugeMessageRow.tupled, HealthGaugeMessageRow.unapply)
  }

  val healthGaugeMessageTable = TableQuery[HealthGaugeMessageTable]
}
