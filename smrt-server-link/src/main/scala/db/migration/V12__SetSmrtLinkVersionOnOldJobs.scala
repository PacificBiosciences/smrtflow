package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

/**
  * Explicitly set old jobs SMRT Link System version to a non-null value to
  * clearly communicate the job is from an "old" version (before this was tracked).
  *
  * This makes it clear the the jobs are indeed old and avoids confusion with the system
  * being not being configured with a SMRT Link System version.
  */
class V12__SetSmrtLinkVersionOnOldJobs
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      sqlu"""UPDATE engine_jobs SET smrtlink_version = '0.0.0' WHERE smrtlink_version IS NULL"""
    )
  }
}
