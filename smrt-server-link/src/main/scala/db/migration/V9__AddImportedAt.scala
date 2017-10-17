package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

// scalastyle:off
class V9__AddImportedAt
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""ALTER TABLE engine_jobs ADD COLUMN imported_at TIMESTAMP DEFAULT NULL"""
      ))
  }
}
