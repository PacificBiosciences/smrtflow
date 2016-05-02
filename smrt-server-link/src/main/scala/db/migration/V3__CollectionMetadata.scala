package db.migration

import java.sql.SQLException
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat


class V3__CollectionMetadata extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(implicit session: Session) {

    session.withTransaction {
      V3Schema.collectionMetadata.ddl.create
    }
  }
}

object V3Schema extends PacBioDateTimeDatabaseFormat {

  class CollectionMetadataT(tag: Tag) extends Table[(Long, UUID, String, Option[String], Option[String], String, String, String, String, Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])](tag, "COLLECTION_METADATA") {
    def runId: Column[Long] = column[Long]("RUN_ID")
    def run = foreignKey("RUN_FK", runId, InitialSchema.runDesignSummaries)(_.id)

    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Column[String] = column[String]("NAME")

    def summary: Column[Option[String]] = column[Option[String]]("COLUMN")

    def context: Column[Option[String]] = column[Option[String]]("CONTEXT")

    def status: Column[String] = column[String]("STATUS")

    def instrumentId: Column[String] = column[String]("INSTRUMENT_ID")

    def instrumentName: Column[String] = column[String]("INSTRUMENT_NAME")

    def wellName: Column[String] = column[String]("WELL_NAME")

    def movieMinutes: Column[Double] = column[Double]("MOVIE_MINUTES")

    def startedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def terminationInfo: Column[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def * : ProvenShape[(Long, UUID, String, Option[String], Option[String], String, String, String, String, Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])] = (runId, uniqueId, name, summary, context, status, instrumentId, instrumentName, wellName, movieMinutes, startedAt, completedAt, terminationInfo)
  }

  lazy val collectionMetadata = TableQuery[CollectionMetadataT]
}
