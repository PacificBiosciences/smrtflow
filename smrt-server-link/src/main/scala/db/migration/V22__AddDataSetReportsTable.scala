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

  def qInsertDataSetReports: DBIO[Int] =
    sqlu"INSERT INTO dataset_reports (dataset_uuid, report_uuid) SELECT dataset_metadata.uuid, datastore_files.uuid FROM dataset_metadata JOIN datastore_files ON (datastore_files.job_id = dataset_metadata.job_id AND datastore_files.file_type_id = 'PacBio.FileTypes.JsonReport' AND dataset_metadata.is_active = true)";

  override def slickMigrate(db: DatabaseDef): Future[Any] =
    db.run(
      DBIO.seq(
        BaseLine.datasetReports.schema.create,
        qInsertDataSetReports
      )
    )
}
