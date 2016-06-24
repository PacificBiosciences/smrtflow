package db.migration

import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.H2Driver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class V6__OptionalRunFields extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    val oldRuns = V4Schema.runSummaries.result
    val newRuns = oldRuns.map(_.map( o => (o._1, o._2, Some(o._3), o._4, o._5, o._6, o._7, o._8, o._9, o._10, o._11, Some(o._12), Some(o._13), Some(o._14), Some(o._15), Some(o._16), o._17, o._18)))

    val data = V4Schema.dataModels.result

    val oldCols = V4Schema.collectionMetadata.result
    val newCols = oldCols.map(_.map( o => (o._1, o._2, o._3, o._4, o._5, o._6, o._7, Some(o._8), Some(o._9), o._10, o._11, o._12, o._13)))
      
    V4Schema.runTables.map(_.schema).reduce(_ ++ _).drop
    V6Schema.runTables.map(_.schema).reduce(_ ++ _).create

    db.run(DBIO.seq(
      newRuns.flatMap(V6Schema.runSummaries ++= _),
      data.flatMap(V6Schema.dataModels ++= _),
      newCols.flatMap(V6Schema.collectionMetadata ++= _)
    ))
  }
}

object V6Schema extends PacBioDateTimeDatabaseFormat {

  class RunSummariesT(tag: Tag) extends Table[(UUID, String, Option[String], Option[String], Option[JodaDateTime], Option[JodaDateTime], Option[JodaDateTime], String, Int, Int, Int, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Boolean)](tag, "RUN_SUMMARIES") {

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def summary: Rep[Option[String]] = column[Option[String]]("SUMMARY")

    def createdBy: Rep[Option[String]] = column[Option[String]]("CREATED_BY")

    def createdAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("CREATED_AT")

    def startedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def status: Rep[String] = column[String]("STATUS")

    def totalCells: Rep[Int] = column[Int]("TOTAL_CELLS")

    def numCellsCompleted: Rep[Int] = column[Int]("NUM_CELLS_COMPLETED")

    def numCellsFailed: Rep[Int] = column[Int]("NUM_CELLS_FAILED")

    def instrumentName: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")

    def instrumentSerialNumber: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_SERIAL_NUMBER")

    def instrumentSwVersion: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_SW_VERSION")

    def primaryAnalysisSwVersion: Rep[Option[String]] = column[Option[String]]("PRIMARY_ANALYSIS_SW_VERSION")

    def context: Rep[Option[String]] = column[Option[String]]("CONTEXT")

    def terminationInfo: Rep[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def reserved: Rep[Boolean] = column[Boolean]("RESERVED")

    def * : ProvenShape[(UUID, String, Option[String], Option[String], Option[JodaDateTime], Option[JodaDateTime], Option[JodaDateTime], String, Int, Int, Int, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Boolean)] = (uniqueId, name, summary, createdBy, createdAt, startedAt, completedAt, status, totalCells, numCellsCompleted, numCellsFailed, instrumentName, instrumentSerialNumber, instrumentSwVersion, primaryAnalysisSwVersion, context, terminationInfo, reserved)
  }

  class DataModelsT(tag: Tag) extends Table[(String, UUID)](tag, "DATA_MODELS") {
    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    // SQLite treats all String columns as TEXT. The size limit on such columns is given by
    // SQLITE_MAX_LENGTH, which defaults to one billion bytes. This should be enough to store
    // a run design model, but if necessary, this value can be raised or lowered at runtime with
    // -DSQLITE_MAX_LENGTH=123456789
    def dataModel: Rep[String] = column[String]("DATA_MODEL")

    def * : ProvenShape[(String, UUID)] = (dataModel, uniqueId)

    def summary = foreignKey("SUMMARY_FK", uniqueId, runSummaries)(_.uniqueId)
  }

  class CollectionMetadataT(tag: Tag) extends Table[(UUID, UUID, String, String, Option[String], Option[String], String, Option[String], Option[String], Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])](tag, "COLLECTION_METADATA") {
    def runId: Rep[UUID] = column[UUID]("RUN_ID")
    def run = foreignKey("RUN_FK", runId, runSummaries)(_.uniqueId)

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def well: Rep[String] = column[String]("WELL")

    def name: Rep[String] = column[String]("NAME")

    def summary: Rep[Option[String]] = column[Option[String]]("COLUMN")

    def context: Rep[Option[String]] = column[Option[String]]("CONTEXT")

    def status: Rep[String] = column[String]("STATUS")

    def instrumentId: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_ID")

    def instrumentName: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")

    def movieMinutes: Rep[Double] = column[Double]("MOVIE_MINUTES")

    def startedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def terminationInfo: Rep[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def * : ProvenShape[(UUID, UUID, String, String, Option[String], Option[String], String, Option[String], Option[String], Double, Option[JodaDateTime], Option[JodaDateTime], Option[String])] = (runId, uniqueId, name, well, summary, context, status, instrumentId, instrumentName, movieMinutes, startedAt, completedAt, terminationInfo)
  }

  lazy val runSummaries = TableQuery[RunSummariesT]
  lazy val dataModels = TableQuery[DataModelsT]
  lazy val collectionMetadata = TableQuery[CollectionMetadataT]

  lazy val runTables: Set[TableQuery[_ <: Table[_]]] = Set(runSummaries, dataModels, collectionMetadata)
}
