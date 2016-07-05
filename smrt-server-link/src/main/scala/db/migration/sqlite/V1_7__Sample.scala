package db.migration.sqlite

import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import db.migration.SlickMigration
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.Future

class V1_7__Sample extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = db.run(V1_7Schema.samples.schema.create)
}

object V1_7Schema extends PacBioDateTimeDatabaseFormat {

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