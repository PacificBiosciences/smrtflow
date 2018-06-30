package db.migration

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.JdbcBackend.DatabaseDef

import com.pacbio.secondary.smrtlink.database.legacy.BaseLine
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes

class V22__AddDataSetReportsTable
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  def qCreate: DBIO[Int] =
    sqlu"""create table "dataset_reports" ("dataset_uuid" UUID NOT NULL,"report_uuid" UUID NOT NULL); create index "index_dataset_uuid" on "dataset_reports" ("dataset_uuid"); create index "index_report_uuid" on "dataset_reports" ("report_uuid"); alter table "dataset_reports" add constraint "rpt_fk" foreign key("report_uuid") references "datastore_files"("uuid") on update NO ACTION on delete NO ACTION"""

  def qInsertDataSetReports: DBIO[Int] =
    sqlu"INSERT INTO dataset_reports (dataset_uuid, report_uuid) SELECT dataset_metadata.uuid, datastore_files.uuid FROM dataset_metadata JOIN datastore_files ON (datastore_files.job_id = dataset_metadata.job_id AND datastore_files.file_type_id = 'PacBio.FileTypes.JsonReport')";

  override def slickMigrate(db: DatabaseDef): Future[Any] =
    db.run(
      DBIO.seq(
        qCreate,
        qInsertDataSetReports
      )
    )
}
