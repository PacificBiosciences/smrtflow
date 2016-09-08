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
      sqlu"create index engine_jobs_job_type on engine_jobs (job_type_id)",
      sqlu"create index engine_jobs_datasets_job_id on engine_jobs_datasets (job_id)",
      sqlu"create index job_events_job_id on job_events (job_id)",
      sqlu"create index dataset_metadata_uuid on dataset_metadata (uuid)",
      sqlu"create index dataset_metadata_project_id on dataset_metadata (project_id)",
      sqlu"create index datastore_files_uuid on datastore_files (uuid)",
      sqlu"create index datastore_files_job_id on datastore_files (job_id)",
      sqlu"create index datastore_files_job_uuid on datastore_files (job_uuid)",
      sqlu"create index projects_users_login on projects_users (login)",
      sqlu"create index projects_users_project_id on projects_users (project_id)",
      sqlu"create index collection_metadata_run_id on collection_metadata (run_id)"
    ))
  }

}
