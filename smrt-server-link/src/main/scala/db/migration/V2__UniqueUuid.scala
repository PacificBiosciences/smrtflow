package db.migration


import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import com.pacbio.secondary.smrtlink.database.legacy.BaseLine

import scala.concurrent.Future

// scalastyle:off
class V2__UniqueUuid extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {

    val clearDupes = DBIO.seq(
        // uniqueify dataset UUIDs
        // ds_ids_to_keep shouldn't exist, but just to be defensive:
        sqlu"drop table if exists ds_ids_to_keep",
        // of the rows with matching UUIDs, keep the most recently updated
        sqlu"""
            create temp table ds_ids_to_keep as (
                select distinct on (uuid) id, uuid
                from dataset_metadata
                order by uuid, updated_at DESC
            )
        """,
        // and delete the others
        sqlu"delete from dataset_metadata       where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from dataset_gmapreferences where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from dataset_hdfsubreads    where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from dataset_references     where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from dataset_subreads       where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from datasets_alignments    where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from datasets_barcodes      where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from datasets_ccsalignments where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from datasets_ccsreads      where id not in (select id from ds_ids_to_keep)",
        sqlu"delete from datasets_contigs       where id not in (select id from ds_ids_to_keep)",

        // uniqueify job UUIDs
        sqlu"""
            create temp table job_ids_to_keep as (
                select distinct on (uuid) job_id, uuid
                from engine_jobs
                order by uuid, updated_at DESC
            )
        """,
        sqlu"delete from engine_jobs where job_id not in (select job_id from job_ids_to_keep)",

        // recreate indexes as unique
        sqlu"drop index if exists dataset_metadata_uuid",
        sqlu"create unique index dataset_metadata_uuid on dataset_metadata (uuid)",
        sqlu"drop index if exists engine_jobs_uuid",
        sqlu"create unique index engine_jobs_uuid on engine_jobs (uuid)",
        sqlu"drop table if exists ds_ids_to_keep",
        sqlu"drop table if exists job_ids_to_keep"
    )

    db.run(clearDupes)
  }

}
