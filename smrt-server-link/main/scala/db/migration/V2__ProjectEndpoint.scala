package db.migration

import java.sql.SQLException

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat


class V2__ProjectEndpoint extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(implicit session: Session) {

    session.withTransaction {
      InitialSchema.projectsUsers.ddl.drop
      InitialSchema.projects.ddl.drop

      V2Schema.projects.ddl.create
      V2Schema.projectsUsers.ddl.create

      V2Schema.projects += (1, "General Project", "General Project", "CREATED", JodaDateTime.now(), JodaDateTime.now())
    }
  }
}

object V2Schema extends PacBioDateTimeDatabaseFormat {
  class ProjectsT(tag: Tag) extends Table[(Int, String, String, String, JodaDateTime, JodaDateTime)](tag, "projects") {

    def id: Column[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def description: Column[String] = column[String]("description")

    def state: Column[String] = column[String]("state")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, String, JodaDateTime, JodaDateTime)] = (id, name, description, state, createdAt, updatedAt)
  }

  class ProjectsUsersT(tag: Tag) extends Table[(Int, String, String)](tag, "projects_users") {
 
    def projectId: Column[Int] = column[Int]("project_id")
 
    def login: Column[String] = column[String]("login")
 
    def role: Column[String] = column[String]("role")
 
    def projectFK = foreignKey("project_fk", projectId, InitialSchema.projects)(a => a.id)
 
    def * : ProvenShape[(Int, String, String)] = (projectId, login, role)
  }

  lazy val projects = TableQuery[ProjectsT]
  lazy val projectsUsers = TableQuery[ProjectsUsersT]
}
