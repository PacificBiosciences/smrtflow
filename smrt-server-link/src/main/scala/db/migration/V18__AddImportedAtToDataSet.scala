package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class V18__AddImportedAtToDataSet
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""ALTER TABLE dataset_metadata ADD COLUMN imported_at TIMESTAMP DEFAULT NULL""",
        sqlu"""UPDATE dataset_metadata SET imported_at = created_at""",
        sqlu"""ALTER TABLE dataset_metadata ALTER COLUMN imported_at SET NOT NULL"""
      ))
  }

}
