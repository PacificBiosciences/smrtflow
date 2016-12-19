package db.migration

import scala.concurrent.Future

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

class V23__JobEventsIndex extends JdbcMigration with SlickMigration {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(sqlu"create index job_events_job_id on job_events (job_id)")
  }

}
