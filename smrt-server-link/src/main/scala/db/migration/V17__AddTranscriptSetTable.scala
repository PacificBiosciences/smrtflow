package db.migration

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.PostgresProfile.api._

import slick.jdbc.JdbcBackend.DatabaseDef

import com.pacbio.secondary.smrtlink.database.TableModels
import com.pacbio.secondary.smrtlink.models.ServiceDataSetMetaType

import scala.concurrent.Future

class V17__AddTranscriptSetTable
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {

    db.run(
      DBIO.seq(
        TableModels.dsTranscript2.schema.create,
        TableModels.datasetMetaTypes ++= Seq(ServiceDataSetMetaType(
          "PacBio.DataSet.TranscriptSet",
          "Display name for PacBio.DataSet.TranscriptSet",
          "Description for PacBio.DataSet.TranscriptSet",
          JodaDateTime.now(),
          JodaDateTime.now(),
          "transcripts"
        ))
      ))
  }

}
