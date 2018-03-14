package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class V16__AddTagsToEngineJob
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(DBIO.seq(
      sqlu"""ALTER TABLE engine_jobs ADD COLUMN tags VARCHAR(256) NOT NULL DEFAULT ''"""))
  }
}
