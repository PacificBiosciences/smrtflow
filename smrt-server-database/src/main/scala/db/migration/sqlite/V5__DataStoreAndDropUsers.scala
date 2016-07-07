package db.migration.sqlite

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import db.migration.SlickMigration
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class V5__DataStoreAndDropUsers extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    def addEmptyNameAndDescription(f: (UUID, String, String, Long, Long, Long, Long, String, Int, UUID)): (UUID, String, String, Long, Long, Long, Long, String, Int, UUID, String, String) =
      (f._1, f._2, f._3, f._4, f._5, f._6, f._7, f._8, f._9, f._10, "", "")

    db.run {
      val updateDatstoreFiles = InitialSchema.datastoreServiceFiles.result.flatMap { files =>
        InitialSchema.datastoreServiceFiles.schema.drop >>
          V5Schema.datastoreServiceFiles.schema.create >>
          (V5Schema.datastoreServiceFiles ++= files.map(addEmptyNameAndDescription))
      }

      val dropUsers = InitialSchema.users.schema.drop

      DBIO.seq(updateDatstoreFiles, dropUsers)
    }
  }
}
object V5Schema {
  class PacBioDataStoreFileT(tag: Tag) extends Table[(UUID, String, String, Long, Long, Long, Long, String, Int, UUID, String, String)](tag, "datastore_files") {
    def uuid: Rep[UUID] = column[UUID]("uuid", O.PrimaryKey)

    def fileTypeId: Rep[String] = column[String]("file_type_id")

    def sourceId: Rep[String] = column[String]("source_id")

    def fileSize: Rep[Long] = column[Long]("file_size")

    def createdAt: Rep[Long] = column[Long]("created_at")

    def modifiedAt: Rep[Long] = column[Long]("modified_at")

    def importedAt: Rep[Long] = column[Long]("imported_at")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jobId: Rep[Int] = column[Int]("job_id")

    def jobUUID: Rep[UUID] = column[UUID]("job_uuid")

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def * : ProvenShape[(UUID, String, String, Long, Long, Long, Long, String, Int, UUID, String, String)] = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID, name, description)
  }

  lazy val datastoreServiceFiles = TableQuery[PacBioDataStoreFileT]
}

