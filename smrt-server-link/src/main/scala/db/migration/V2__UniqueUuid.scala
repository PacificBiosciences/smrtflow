package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.PostgresDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import scala.concurrent.Future

// scalastyle:off
class V2__UniqueUuid
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {

    val clearDupes = DBIO.seq(
      // uniquify dataset UUIDs
      // these temp tables shouldn't exist, but just to be defensive:
      sqlu"drop table if exists all_dataset_type_rows",
      sqlu"drop table if exists dupe_ds_uuids",
      sqlu"drop table if exists ds_ids_to_keep",
      sqlu"drop table if exists ds_ids_to_delete",
      // get all the id/uuid values from the per-dataset-type tables
      sqlu"""
            create temp table all_dataset_type_rows as (
                select id, uuid from dataset_gmapreferences union all
                select id, uuid from dataset_hdfsubreads    union all
                select id, uuid from dataset_references     union all
                select id, uuid from dataset_subreads       union all
                select id, uuid from datasets_alignments    union all
                select id, uuid from datasets_barcodes      union all
                select id, uuid from datasets_ccsalignments union all
                select id, uuid from datasets_ccsreads      union all
                select id, uuid from datasets_contigs
            )
        """,
      // get the uuids that are duplicated in dataset_metadata
      sqlu"""
            create temp table dupe_ds_uuids as (
                select uuid, count(*) as count
                from dataset_metadata
                group by uuid
                having count(*) > 1
            )
        """,
      // for each uuid, keep one row (the latest) that has a
      // corresponding row in a per-dataset-type table
      sqlu"""
            create temp table ds_ids_to_keep as (
                select distinct on (uuid) id, uuid
                from dataset_metadata
                where id in (select id from all_dataset_type_rows)
                order by uuid, updated_at DESC
            )
        """,
      // delete rows with duplicate UUIDs that we're not keeping
      sqlu"""
            create temp table ds_ids_to_delete as (
                select id from dataset_metadata
                where id not in (select id from ds_ids_to_keep)
                  and uuid in (select uuid from dupe_ds_uuids)
            )
        """,
      sqlu"delete from dataset_metadata       where id in (select id from ds_ids_to_delete)",
      sqlu"delete from dataset_gmapreferences where id in (select id from ds_ids_to_delete)",
      sqlu"delete from dataset_hdfsubreads    where id in (select id from ds_ids_to_delete)",
      sqlu"delete from dataset_references     where id in (select id from ds_ids_to_delete)",
      sqlu"delete from dataset_subreads       where id in (select id from ds_ids_to_delete)",
      sqlu"delete from datasets_alignments    where id in (select id from ds_ids_to_delete)",
      sqlu"delete from datasets_barcodes      where id in (select id from ds_ids_to_delete)",
      sqlu"delete from datasets_ccsalignments where id in (select id from ds_ids_to_delete)",
      sqlu"delete from datasets_ccsreads      where id in (select id from ds_ids_to_delete)",
      sqlu"delete from datasets_contigs       where id in (select id from ds_ids_to_delete)",
      // uniquify job UUIDs
      sqlu"drop table if exists job_ids_to_keep",
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
      // drop temp tables
      sqlu"drop table if exists all_dataset_type_rows",
      sqlu"drop table if exists dupe_ds_uuids",
      sqlu"drop table if exists ds_ids_to_keep",
      sqlu"drop table if exists ds_ids_to_delete",
      sqlu"drop table if exists job_ids_to_keep"
    )

    db.run(clearDupes)
  }

}
