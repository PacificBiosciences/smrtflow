package db.migration.sqlite

import java.util.UUID

import db.migration.SlickMigration
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.Future

class V7__Sample extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = db.run(V7Schema.samples.schema.create)
}

object V7Schema {

  class SampleT(tag: Tag) extends Table[(String, UUID, String, String, Long)](tag, "SAMPLE") {

    def details: Rep[String] = column[String]("DETAILS")

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def createdBy: Rep[String] = column[String]("CREATED_BY")

    def createdAt: Rep[Long] = column[Long]("CREATED_AT")

    def * : ProvenShape[(String, UUID, String, String, Long)] = (details, uniqueId, name, createdBy, createdAt)
  }

  lazy val samples = TableQuery[SampleT]
}