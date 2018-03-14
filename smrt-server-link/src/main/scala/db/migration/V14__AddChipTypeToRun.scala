package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class V14__AddChipTypeToRun
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""ALTER TABLE run_summaries ADD COLUMN chip_type VARCHAR(256) NOT NULL DEFAULT '1mChip'""",
        sqlu"""UPDATE run_summaries SET chip_type = '1mChip'"""
      ))
  }
}
