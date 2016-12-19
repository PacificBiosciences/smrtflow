package db.migration

import scala.concurrent.Future

import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

/**
  * Created by mkocher on 9/7/16.
  *
  * This migrate is duplicated from V11_1 where V11 added indexes and V11_1 dropped the Engine Table
  *
  * In sqlite, when a table is dropped, the indexes are also deleted.
  *
  * The next migration for Engine Jobs should use a consistent model of Slick interface and
  * add index_idx def idx = index("idx_job_id", jobId, unique = true) to the Table
  *
  */
class V17__AddIndexToEngineJobs extends JdbcMigration with SlickMigration {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(DBIO.seq(
      sqlu"create index engine_jobs_uuid on engine_jobs (uuid)",
      sqlu"create index engine_jobs_job_type on engine_jobs (job_type_id)"
    ))
  }

}
