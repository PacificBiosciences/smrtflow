package db.migration

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future


class V20__ProjectDelete extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    db.run(V16Schema.projects.result).flatMap { oldProjects =>
      val newProjects = oldProjects.map(p => (p._1, p._2, p._3, p._4, p._5, p._6, true))
      db.run(DBIO.seq(
        V16Schema.projects.schema.drop,
        V20Schema.projects.schema.create,
        V20Schema.projects ++= newProjects,
        V20Schema.projectNameUnique
      ))
    }
  }
}

object V20Schema extends PacBioDateTimeDatabaseFormat {
  class ProjectsT(tag: Tag) extends Table[(Int, String, String, String, JodaDateTime, JodaDateTime, Boolean)](tag, "projects") {

    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def state: Rep[String] = column[String]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * : ProvenShape[(Int, String, String, String, JodaDateTime, JodaDateTime, Boolean)] = (id, name, description, state, createdAt, updatedAt, isActive)
  }

  // ideally this would be done using the slick Table.index method, but I don't see how
  // to create partial indexes that way.
  val projectNameUnique = sqlu"create unique index project_name_unique on projects (name) where is_active"

  lazy val projects = TableQuery[ProjectsT]
}
