package db.migration

import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.H2Driver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat

import scala.concurrent.Future


class V3__CollectionMetadata extends JdbcMigration with SlickMigration with LazyLogging {
  override def slickMigrate(db: DatabaseDef): Future[Any] = db.run(V3Schema.collectionMetadata.schema.create)
}

object V3Schema extends PacBioDateTimeDatabaseFormat {

  class CollectionMetadataT(tag: Tag) extends Table[(Long, UUID, String, Option[String], Option[String], String, String, String, String, Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])](tag, "COLLECTION_METADATA") {
    def runId: Rep[Long] = column[Long]("RUN_ID")
    def run = foreignKey("RUN_FK", runId, InitialSchema.runDesignSummaries)(_.id)

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def summary: Rep[Option[String]] = column[Option[String]]("COLUMN")

    def context: Rep[Option[String]] = column[Option[String]]("CONTEXT")

    def status: Rep[String] = column[String]("STATUS")

    def instrumentId: Rep[String] = column[String]("INSTRUMENT_ID")

    def instrumentName: Rep[String] = column[String]("INSTRUMENT_NAME")

    def wellName: Rep[String] = column[String]("WELL_NAME")

    def movieMinutes: Rep[Double] = column[Double]("MOVIE_MINUTES")

    def startedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def terminationInfo: Rep[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def * : ProvenShape[(Long, UUID, String, Option[String], Option[String], String, String, String, String, Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])] = (runId, uniqueId, name, summary, context, status, instrumentId, instrumentName, wellName, movieMinutes, startedAt, completedAt, terminationInfo)
  }

  lazy val collectionMetadata = TableQuery[CollectionMetadataT]
}
