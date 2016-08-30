package db.migration

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future


class V16__MakeProjectNamesUnique extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(V2Schema.projects.result).flatMap { oldProjects =>
      val dupeNames = oldProjects.groupBy(_._2).filter(t => t._2.length > 1).keys
      if (!dupeNames.isEmpty) {
        val dupeList = dupeNames.mkString(", ")
        throw new RuntimeException(s"name(s) $dupeList used for multiple projects")
      }
      db.run(DBIO.seq(
        V2Schema.projects.schema.drop,
        V16Schema.projects.schema.create,
        V16Schema.projects ++= oldProjects
      ))
    }
  }
}

object V16Schema extends PacBioDateTimeDatabaseFormat {
  class ProjectsT(tag: Tag) extends Table[(Int, String, String, String, JodaDateTime, JodaDateTime)](tag, "projects") {

    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def state: Rep[String] = column[String]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def nameUnique = index("project_name_unique", name, unique = true)

    def * : ProvenShape[(Int, String, String, String, JodaDateTime, JodaDateTime)] = (id, name, description, state, createdAt, updatedAt)
  }

  lazy val projects = TableQuery[ProjectsT]
}
