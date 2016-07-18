package com.pacbio.database

import java.util.concurrent.Future

import org.specs2.mutable.Specification
import slick.lifted.{Rep, TableQuery, Tag}
import slick.driver.H2Driver.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object H2TestTableModels {

  // example case class for serialization
  case class Project(id: Int, name: String)

  case class ProjectUser(projectId: Int, login: String, role: String)


  lazy val projects = TableQuery[ProjectsT]
  lazy val projectsUsers = TableQuery[ProjectsUsersT]

  class ProjectsT(tag: Tag) extends Table[Project](tag, "projects") {

    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def * = (id, name) <>(Project.tupled, Project.unapply)
  }

  class ProjectsUsersT(tag: Tag) extends Table[ProjectUser](tag, "projects_users") {

    def projectId: Rep[Int] = column[Int]("project_id")

    def login: Rep[String] = column[String]("login")

    def role: Rep[String] = column[String]("role")

    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)

    def * = (projectId, login, role) <>(ProjectUser.tupled, ProjectUser.unapply)
  }
}


/**
 * Exercises basic Slick
 *
 * @author Jayson Falkner - jfalkner@pacificbiosciences.com
 */
class H2SlickUsageSpec extends Specification {

  // force these tests to run sequentially since they can lock up the database
  sequential

  val db = new Database("jdbc:h2:mem:")

  import H2TestTableModels._

  "H2 RDMS" should {
    "Has the test table schema" in {
      db.run(projects.schema.create)
      1 mustEqual 1
    }
    "Can write and read tables via Slick" in {
      val p1 = Project(1, "Project 1")
      val p2 = Project(2, "Project 2")
      // write a project and a user
      val f = db.run(DBIO.seq(
        projects += p1,
        projects += p2,
        projectsUsers += ProjectUser(p1.id, "jfalkner", "OWNER")))
      Await.ready(f, Duration(5, "seconds"))

      // read the projects
      val output2 = for {
        p <- projects
      } yield p
      val tuples: Seq[Project] = Await.result(db.run(output2.result), Duration(5, "seconds"))

      tuples mustEqual Seq(p1, p2)
    }
  }
}


