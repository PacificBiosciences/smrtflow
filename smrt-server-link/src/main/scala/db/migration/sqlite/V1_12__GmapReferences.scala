package db.migration.sqlite

import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import com.typesafe.scalalogging.LazyLogging
import db.migration.SlickMigration
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.Future


class V1_12__GmapReferences extends JdbcMigration with SlickMigration with LazyLogging {
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run {
      V1_12Schema.gmapReference.schema.create >> _populateGmapDataSetType
    }
  }

  def _populateGmapDataSetType: DBIOAction[Any, NoStream, _ <: Effect] = {
    val dsTypeRows = List(
      ("PacBio.DataSet.GmapReferenceSet", "Display name for PacBio.DataSet.GmapReferenceSet", "Description for PacBio.DataSet.GmapReferenceSet", JodaDateTime.now(), JodaDateTime.now(), "gmapreferences")
    )
    InitialSchema.datasetTypes ++= dsTypeRows
  }
}


object V1_12Schema extends PacBioDateTimeDatabaseFormat {

  // XXX copied from initial schema...
  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class GmapReferenceDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String)](tag, "dataset_gmapreferences") {

    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * : ProvenShape[(Int, UUID, String, String)] = (id, uuid, ploidy, organism)

  }

  lazy val gmapReference = TableQuery[GmapReferenceDataSetT]
}
