package com.pacbio.common.database

import java.util.UUID

import com.pacbio.common.models.{HealthGaugeMessage, HealthSeverity}
import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import org.joda.time.{DateTime => JodaDateTime}

import slick.driver.SQLiteDriver.api._

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
    def id: Rep[String] = column[String]("ID")
    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")
    def uuid: Rep[UUID] = column[UUID]("UUID" /*, O.PrimaryKey */)
    def message: Rep[String] = column[String]("MESSAGE")
    def severity: Rep[HealthSeverity.HealthSeverity] = column[HealthSeverity.HealthSeverity]("SEVERITY")
    def sourceId: Rep[String] = column[String]("SOURCE_ID")

    def healthGaugeMessage =
      (createdAt, uuid, message, severity, sourceId) <> (HealthGaugeMessage.tupled, HealthGaugeMessage.unapply)
    def * = (id, healthGaugeMessage) <> (HealthGaugeMessageRow.tupled, HealthGaugeMessageRow.unapply)
  }

  val healthGaugeMessageTable = TableQuery[HealthGaugeMessageTable]
}
