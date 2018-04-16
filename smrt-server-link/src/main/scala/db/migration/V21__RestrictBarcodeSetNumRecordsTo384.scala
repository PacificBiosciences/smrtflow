package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class V21__RestrictBarcodeSetNumRecordsTo384
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""UPDATE dataset_metadata SET is_active = false, updated_at = CURRENT_TIMESTAMP FROM datasets_barcodes WHERE dataset_metadata.id = datasets_barcodes.id AND dataset_metadata.num_records > 384"""
      ))
  }

}
