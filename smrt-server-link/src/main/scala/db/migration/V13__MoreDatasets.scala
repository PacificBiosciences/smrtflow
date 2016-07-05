
package db.migration


import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.Future


class V13__MoreDatasets extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run {
      V13Schema.contigs.schema.create >> V13Schema.ccsalignments.schema.create
    }
  }
}

object V13Schema extends PacBioDateTimeDatabaseFormat {

  // XXX copied from initial schema...
  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class ContigDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_contigs") {
    def * : ProvenShape[(Int, UUID)] = (id, uuid)
  }

  class ConsensusAlignmentDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_ccsalignments") {
    def * : ProvenShape[(Int, UUID)] = (id, uuid)
  }

  lazy val contigs = TableQuery[ContigDataSetT]
  lazy val ccsalignments = TableQuery[ConsensusAlignmentDataSetT]

}
