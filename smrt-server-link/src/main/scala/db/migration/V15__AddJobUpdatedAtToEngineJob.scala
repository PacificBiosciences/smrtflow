package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class V15__AddJobUpdatedAtToEngineJob
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  /**
    * This is a bit borked, but there's no simple way of getting when the job
    * execution last updated. Hence we copy the last "updated_at" record.
    */
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""ALTER TABLE engine_jobs ADD COLUMN job_updated_at TIMESTAMP DEFAULT NULL""",
        sqlu"""UPDATE engine_jobs SET job_updated_at = updated_at""",
        sqlu"""ALTER TABLE engine_jobs ALTER COLUMN job_updated_at SET NOT NULL"""
      ))
  }
}
