package db.migration

import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import org.flywaydb.core.api.migration.jdbc.JdbcMigration

import slick.driver.SQLiteDriver.api._
import slick.lifted.ProvenShape

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat

class V7__Sample extends JdbcMigration with SlickMigration {
  override def slickMigrate: DBIOAction[Any, NoStream, Nothing] =
    V7Schema.samples.schema.create
}

object V7Schema extends PacBioDateTimeDatabaseFormat {

  class SampleT(tag: Tag) extends Table[(String, UUID, String, String, JodaDateTime)](tag, "SAMPLE") {

    def details: Rep[String] = column[String]("DETAILS")

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def createdBy: Rep[String] = column[String]("CREATED_BY")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def * : ProvenShape[(String, UUID, String, String, JodaDateTime)] = (details, uniqueId, name, createdBy, createdAt)
  }

  lazy val samples = TableQuery[SampleT]
}