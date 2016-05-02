package db.migration

import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration

import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape
import com.pacbio.common.time.PacBioDateTimeDatabaseFormat

class V7__Sample extends JdbcMigration with SlickMigration {
  override def slickMigrate(implicit session: Session) {
    session.withTransaction {
      V7Schema.samples.ddl.create
    }
  }
}

object V7Schema extends PacBioDateTimeDatabaseFormat {

  class SampleT(tag: Tag) extends Table[(String, UUID, String, String, JodaDateTime)](tag, "SAMPLE") {

    def details: Column[String] = column[String]("DETAILS")

    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Column[String] = column[String]("NAME")

    def createdBy: Column[String] = column[String]("CREATED_BY")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def * : ProvenShape[(String, UUID, String, String, JodaDateTime)] = (details, uniqueId, name, createdBy, createdAt)
  }

  lazy val samples = TableQuery[SampleT]
}