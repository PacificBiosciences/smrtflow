package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

// scalastyle:off
class V10__SetBioSampleName
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      sqlu"""UPDATE dataset_subreads SET bio_sample_name = well_sample_name WHERE bio_sample_name = 'unknown'"""
    )
  }
}
