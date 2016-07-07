package db.migration.sqlite

import java.util.UUID

import db.migration.SlickMigration
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class V10__DropJobStatesTable extends JdbcMigration with SlickMigration {
  val idToState = Map (
    1 -> "CREATED",
    2 -> "SUBMITTED",
    3 -> "RUNNING",
    4 -> "TERMINATED",
    5 -> "SUCCESSFUL",
    6 -> "FAILED",
    7 -> "UNKNOWN"
  )
  
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    val engineJobs = V8Schema.engineJobs.result
    val jobEvents = V8Schema.jobEvents.result

    def engineJobV8toV10(j: (Int, UUID, String, String, Long, Long, Int, String, String, String, Option[String])): (Int, UUID, String, String, Long, Long, String, String, String, String, Option[String]) =
      j.copy(_7 = idToState.getOrElse(j._7, "UNKNOWN"))

    def jobEventV8toV10(e: (UUID, Int, Int, String, Long)): (UUID, Int, String, String, Long) =
      e.copy(_3 = idToState.getOrElse(e._3, "UNKNOWN"))

    db.run(engineJobs.zip(jobEvents).flatMap { data =>
      (InitialSchema.engineJobs.schema ++ InitialSchema.jobEvents.schema ++ InitialSchema.jobStates.schema).drop >>
        (V10Schema.engineJobs.schema ++ V10Schema.jobEvents.schema).create >>
        DBIO.seq(V10Schema.engineJobs ++= data._1.map(engineJobV8toV10), V10Schema.jobEvents ++= data._2.map(jobEventV8toV10))
    })
  }
}

object V8Schema {
  class JobStatesT(tag: Tag) extends Table[(Int, String, String, Long, Long)](tag, "job_states") {

    def id: Rep[Int] = column[Int]("job_state_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def createdAt: Rep[Long] = column[Long]("created_at")

    def updatedAt: Rep[Long] = column[Long]("updated_at")

    def * : ProvenShape[(Int, String, String, Long, Long)] = (id, name, description, createdAt, updatedAt)

  }

  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, Long, Long, Int, String, String, String, Option[String])](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Rep[UUID] = column[UUID]("uuid")

    // this is really comment
    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def stateId: Rep[Int] = column[Int]("state_id")

    def createdAt: Rep[Long] = column[Long]("created_at")

    def updatedAt: Rep[Long] = column[Long]("updated_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jsonSettings: Rep[String] = column[String]("json_settings")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def * : ProvenShape[(Int, UUID, String, String, Long, Long, Int, String, String, String, Option[String])] = (id, uuid, name, pipelineId, createdAt, updatedAt, stateId, jobTypeId, path, jsonSettings, createdBy)
  }

  class JobEventsT(tag: Tag) extends Table[(UUID, Int, Int, String, Long)](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def stateId: Rep[Int] = column[Int]("state_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[Long] = column[Long]("created_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(UUID, Int, Int, String, Long)] = (id, jobId, stateId, message, createdAt)
  }

  lazy val jobStates = TableQuery[JobStatesT]
  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val jobEvents = TableQuery[JobEventsT]

}

object V10Schema {

  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, Long, Long, String, String, String, String, Option[String])](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Rep[UUID] = column[UUID]("uuid")

    // this is really comment
    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def state: Rep[String] = column[String]("state")

    def createdAt: Rep[Long] = column[Long]("created_at")

    def updatedAt: Rep[Long] = column[Long]("updated_at")

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jsonSettings: Rep[String] = column[String]("json_settings")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def * : ProvenShape[(Int, UUID, String, String, Long, Long, String, String, String, String, Option[String])] = (id, uuid, name, pipelineId, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy)
  }

  class JobEventsT(tag: Tag) extends Table[(UUID, Int, String, String, Long)](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def state: Rep[String] = column[String]("state")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[Long] = column[Long]("created_at")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(UUID, Int, String, String, Long)] = (id, jobId, state, message, createdAt)
  }

  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val jobEvents = TableQuery[JobEventsT]
}
