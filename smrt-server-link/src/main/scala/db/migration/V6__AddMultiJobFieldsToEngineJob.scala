package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

/**
  * Created by mkocher on 9/11/17.
  */
class V6__AddMultiJobFieldsToEngineJob
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""ALTER TABLE engine_jobs ADD COLUMN is_multi_job BOOLEAN DEFAULT FALSE NOT NULL """,
        sqlu"""ALTER TABLE engine_jobs ADD COLUMN json_workflow TEXT DEFAULT '{}' NOT NULL """,
        sqlu"""ALTER TABLE engine_jobs ADD COLUMN parent_multi_job_id INTEGER DEFAULT NULL"""
      ))
  }
}
