package db.migration

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

import slick.driver.SQLiteDriver.api._
import slick.lifted.ProvenShape

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat


class V2__ProjectEndpoint extends JdbcMigration with SlickMigration with LazyLogging {
  override def slickMigrate: DBIOAction[Any, NoStream, Nothing] =
    (InitialSchema.projectsUsers.schema ++ InitialSchema.projects.schema).drop >>
    (V2Schema.projectsUsers.schema ++ V2Schema.projects.schema).create >>
    (V2Schema.projects += (1, "General Project", "General Project", "CREATED", JodaDateTime.now(), JodaDateTime.now()))
}

object V2Schema extends PacBioDateTimeDatabaseFormat {
  class ProjectsT(tag: Tag) extends Table[(Int, String, String, String, JodaDateTime, JodaDateTime)](tag, "projects") {

    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def state: Rep[String] = column[String]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, String, JodaDateTime, JodaDateTime)] = (id, name, description, state, createdAt, updatedAt)
  }

  class ProjectsUsersT(tag: Tag) extends Table[(Int, String, String)](tag, "projects_users") {
 
    def projectId: Rep[Int] = column[Int]("project_id")
 
    def login: Rep[String] = column[String]("login")
 
    def role: Rep[String] = column[String]("role")
 
    def projectFK = foreignKey("project_fk", projectId, InitialSchema.projects)(a => a.id)
 
    def * : ProvenShape[(Int, String, String)] = (projectId, login, role)
  }

  lazy val projects = TableQuery[ProjectsT]
  lazy val projectsUsers = TableQuery[ProjectsUsersT]
}
