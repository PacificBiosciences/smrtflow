package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

/**
  * Created by mkocher on 9/18/17.
  */
class V7__AddJobIdToRun
  extends JdbcMigration
  with SlickMigration
  with LazyLogging {

    override def slickMigrate(db: DatabaseDef): Future[Any] = {
      db.run(
        DBIO.seq(
          sqlu"""ALTER TABLE run_summaries ADD COLUMN multi_job_id INTEGER DEFAULT NULL"""
        ))
    }
}
