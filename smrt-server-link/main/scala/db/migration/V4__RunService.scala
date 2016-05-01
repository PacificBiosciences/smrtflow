package db.migration

import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat


class V4__RunService extends JdbcMigration with SlickMigration with LazyLogging {
  override def slickMigrate(implicit session: Session) {
    session.withTransaction {
      V3Schema.collectionMetadata.ddl.drop
      V4Schema.runTables.map(_.ddl).reduce(_ ++ _).create
    }
  }
}

object V4Schema extends PacBioDateTimeDatabaseFormat {

  class RunSummariesT(tag: Tag) extends Table[(UUID, String, String, Option[String], Option[JodaDateTime], Option[JodaDateTime], Option[JodaDateTime], String, Int, Int, Int, String, String, String, String, String, Option[String], Boolean)](tag, "RUN_SUMMARIES") {

    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Column[String] = column[String]("NAME")

    def summary: Column[String] = column[String]("SUMMARY")

    def createdBy: Column[Option[String]] = column[Option[String]]("CREATED_BY")

    def createdAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("CREATED_AT")

    def startedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def status: Column[String] = column[String]("STATUS")

    def totalCells: Column[Int] = column[Int]("TOTAL_CELLS")

    def numCellsCompleted: Column[Int] = column[Int]("NUM_CELLS_COMPLETED")

    def numCellsFailed: Column[Int] = column[Int]("NUM_CELLS_FAILED")

    def instrumentName: Column[String] = column[String]("INSTRUMENT_NAME")

    def instrumentSerialNumber: Column[String] = column[String]("INSTRUMENT_SERIAL_NUMBER")

    def instrumentSwVersion: Column[String] = column[String]("INSTRUMENT_SW_VERSION")

    def primaryAnalysisSwVersion: Column[String] = column[String]("PRIMARY_ANALYSIS_SW_VERSION")

    def context: Column[String] = column[String]("CONTEXT")

    def terminationInfo: Column[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def reserved: Column[Boolean] = column[Boolean]("RESERVED")

    def * : ProvenShape[(UUID, String, String, Option[String], Option[JodaDateTime], Option[JodaDateTime], Option[JodaDateTime], String, Int, Int, Int, String, String, String, String, String, Option[String], Boolean)] = (uniqueId, name, summary, createdBy, createdAt, startedAt, completedAt, status, totalCells, numCellsCompleted, numCellsFailed, instrumentName, instrumentSerialNumber, instrumentSwVersion, primaryAnalysisSwVersion, context, terminationInfo, reserved)
  }

  class DataModelsT(tag: Tag) extends Table[(String, UUID)](tag, "DATA_MODELS") {
    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    // SQLite treats all String columns as TEXT. The size limit on such columns is given by
    // SQLITE_MAX_LENGTH, which defaults to one billion bytes. This should be enough to store
    // a run design model, but if necessary, this value can be raised or lowered at runtime with
    // -DSQLITE_MAX_LENGTH=123456789
    def dataModel: Column[String] = column[String]("DATA_MODEL")

    def * : ProvenShape[(String, UUID)] = (dataModel, uniqueId)

    def summary = foreignKey("SUMMARY_FK", uniqueId, runSummaries)(_.uniqueId)
  }

  class CollectionMetadataT(tag: Tag) extends Table[(UUID, UUID, String, String, Option[String], Option[String], String, String, String, Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])](tag, "COLLECTION_METADATA") {
    def runId: Column[UUID] = column[UUID]("RUN_ID")
    def run = foreignKey("RUN_FK", runId, runSummaries)(_.uniqueId)

    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def well: Column[String] = column[String]("WELL")

    def name: Column[String] = column[String]("NAME")

    def summary: Column[Option[String]] = column[Option[String]]("COLUMN")

    def context: Column[Option[String]] = column[Option[String]]("CONTEXT")

    def status: Column[String] = column[String]("STATUS")

    def instrumentId: Column[String] = column[String]("INSTRUMENT_ID")

    def instrumentName: Column[String] = column[String]("INSTRUMENT_NAME")

    def movieMinutes: Column[Double] = column[Double]("MOVIE_MINUTES")

    def startedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def terminationInfo: Column[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def * : ProvenShape[(UUID, UUID, String, String, Option[String], Option[String], String, String, String, Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])] = (runId, uniqueId, name, well, summary, context, status, instrumentId, instrumentName, movieMinutes, startedAt, completedAt, terminationInfo)
  }

  lazy val runSummaries = TableQuery[RunSummariesT]
  lazy val dataModels = TableQuery[DataModelsT]
  lazy val collectionMetadata = TableQuery[CollectionMetadataT]

  lazy val runTables: Set[TableQuery[_ <: Table[_]]] = Set(runSummaries, dataModels, collectionMetadata)
}
