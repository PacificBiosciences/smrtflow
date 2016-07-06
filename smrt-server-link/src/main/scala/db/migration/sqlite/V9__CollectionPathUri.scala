package db.migration.sqlite

import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import db.migration.SlickMigration
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class V9__CollectionPathUri extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run {
      for {
        newc <- V6Schema.collectionMetadata.result.map(_.map(o => (o._1, o._2, o._3, o._4, o._5, o._6, None, o._7, o._8, o._9, o._10, o._11, o._12, o._13)))
        _    <- V6Schema.collectionMetadata.schema.drop
        _    <- V9Schema.collectionMetadata.schema.create
        _    <- V9Schema.collectionMetadata ++= newc
      } yield ()
    }
  }
}

object V9Schema extends PacBioDateTimeDatabaseFormat {

  class CollectionMetadataT(tag: Tag) extends Table[(UUID, UUID, String, String, Option[String], Option[String], Option[String], String, Option[String], Option[String], Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])](tag, "COLLECTION_METADATA") {
    def runId: Rep[UUID] = column[UUID]("RUN_ID")
    def run = foreignKey("RUN_FK", runId, V6Schema.runSummaries)(_.uniqueId)

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def well: Rep[String] = column[String]("WELL")

    def name: Rep[String] = column[String]("NAME")

    def summary: Rep[Option[String]] = column[Option[String]]("COLUMN")

    def context: Rep[Option[String]] = column[Option[String]]("CONTEXT")

    def collectionPathUri: Rep[Option[String]] = column[Option[String]]("COLLECTION_PATH_URI")

    def status: Rep[String] = column[String]("STATUS")

    def instrumentId: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_ID")

    def instrumentName: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")

    def movieMinutes: Rep[Double] = column[Double]("MOVIE_MINUTES")

    def startedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def terminationInfo: Rep[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def * : ProvenShape[(UUID, UUID, String, String, Option[String], Option[String], Option[String], String, Option[String], Option[String], Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])] = (runId, uniqueId, name, well, summary, context, collectionPathUri, status, instrumentId, instrumentName, movieMinutes, startedAt, completedAt, terminationInfo)
  }

  lazy val collectionMetadata = TableQuery[CollectionMetadataT]
}
