package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

// scalastyle:off
class V8__ExtendSubreadTable
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(DBIO.seq(
      sqlu"""ALTER TABLE dataset_subreads ADD COLUMN dna_barcode_name VARCHAR(256) DEFAULT NULL""",
      sqlu"""ALTER TABLE dataset_metadata ADD COLUMN parent_uuid UUID DEFAULT NULL"""))
  }
}
