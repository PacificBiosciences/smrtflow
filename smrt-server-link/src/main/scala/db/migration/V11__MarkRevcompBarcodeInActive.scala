package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

// scalastyle:off
class V11__MarkRevcompBarcodeInActive
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  /**
    * Mark these as inActive https://jira.pacificbiosciences.com/browse/SL-2003
    *
    * a8bbbede-f25c-273e-da94-47d16f6c0a38 (RSII_96_barcodes_revcomp.barcodeset.xml)
    * a22e749d-6098-999a-483d-2915c874097a (Sequel_RSII_96_barcodes_revcomp_v1.barcodeset.xml)
    * 4de551b9-962e-205d-1dcc-fb6d3db87895 (RSII_384_barcodes_universal.barcodeset.xml)
    *
    */
  override def slickMigrate(db: DatabaseDef): Future[Any] = {

    // Doing this the caveman way to avoid fighting with slick.
    db.run(
      DBIO.seq(
        sqlu"""UPDATE dataset_metadata SET is_active = FALSE WHERE uuid = 'a8bbbede-f25c-273e-da94-47d16f6c0a38'""",
        sqlu"""UPDATE dataset_metadata SET is_active = FALSE WHERE uuid = 'a22e749d-6098-999a-483d-2915c874097a'""",
        sqlu"""UPDATE dataset_metadata SET is_active = FALSE WHERE uuid = '4de551b9-962e-205d-1dcc-fb6d3db87895'"""
      ))
  }
}
