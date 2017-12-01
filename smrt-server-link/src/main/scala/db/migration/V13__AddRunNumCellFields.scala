package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

class V13__AddRunNumCellFields
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""ALTER TABLE run_summaries ADD COLUMN num_standard_cells INTEGER NOT NULL DEFAULT 0""",
        sqlu"""ALTER TABLE run_summaries ADD COLUMN num_lr_cells INTEGER NOT NULL DEFAULT 0""",
        sqlu"""UPDATE run_summaries SET num_standard_cells = total_cells"""
      ))
  }
}
