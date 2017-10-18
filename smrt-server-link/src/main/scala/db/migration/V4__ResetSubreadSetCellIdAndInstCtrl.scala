package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

// scalastyle:off
class V4__ResetSubreadSetCellIdAndInstCtrl
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  /**
    * This will update the hardcoded values to "unknown"
    *
    * 1. cell_id from "cell-id" value to "unknown"
    * 2  instrument_control_version from 'instrument-ctr-version to "unknown"
    *
    * This will make the unknown values at least be consistent by setting
    * these old values to "unknown".
    *
    * This should have probably be set to Option[String] to avoid this. I don't
    * recall the historical context. This could be friction with parsing the XML.
    *
    */
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""UPDATE dataset_subreads SET cell_id = 'unknown' WHERE cell_id = 'cell-id'""",
        sqlu"""UPDATE dataset_subreads SET instrument_control_version = 'unknown' WHERE cell_id = 'instrument-ctr-version'"""
      ))
  }
}
