package db.migration

import java.sql.JDBCType
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.{
  GetResult,
  PositionedParameters,
  PositionedResult,
  SetParameter
}
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class V20__AddNumChildrenToDataSetMeta
    extends JdbcMigration
    with SlickMigration
    with LazyLogging {

  //
  case class InRecord(id: Int, uuid: UUID)
  case class OutRecord(in: InRecord, numChildren: Int)

  implicit class PgPositionedResult(val r: PositionedResult) {
    def nextUUID: UUID = UUID.fromString(r.nextString)

    def nextUUIDOption: Option[UUID] =
      r.nextStringOption().map(UUID.fromString)
  }

  implicit object SetUUID extends SetParameter[UUID] {
    def apply(v: UUID, pp: PositionedParameters) {
      pp.setObject(v, JDBCType.BINARY.getVendorTypeNumber)
    }
  }

  implicit val getInRecordResult = GetResult(
    r => InRecord(r.nextInt, UUID.fromString(r.nextString)))

  def getRecords: DBIO[Seq[InRecord]] =
    sql"""SELECT id, parent_uuid FROM dataset_metadata WHERE parent_uuid IS NOT NULL"""
      .as[InRecord]

  /**
    * Note, this is assuming the parent exists
    */
  def getAndUpdateNumChildren(record: InRecord): DBIO[String] =
    for {
      numChildren <- sql"""select COUNT(*) from dataset_metadata WHERE parent_uuid = ${record.uuid}"""
        .as[Int]
      _ <- sqlu"""UPDATE dataset_metadata SET num_children = ${numChildren.length} WHERE uuid = ${record.uuid}"""
    } yield s"Updated $record with ${numChildren.length}"

  def getAndUpdateRecords(records: Seq[InRecord]): DBIO[Seq[String]] =
    DBIO.sequence(records.map(getAndUpdateNumChildren))

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(
      DBIO.seq(
        sqlu"""ALTER TABLE dataset_metadata ADD COLUMN num_children INT DEFAULT 0 NOT NULL""",
        for {
          inRecords <- getRecords
          _ <- getAndUpdateRecords(inRecords)
        } yield s"Updated ${inRecords.length}"
      ))
  }

}
