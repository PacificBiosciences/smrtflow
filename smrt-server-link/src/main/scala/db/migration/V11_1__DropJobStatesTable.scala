package db.migration

import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.SQLiteDriver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class V11_1__DropJobStatesTable extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    val engineJobs = V8Schema.engineJobs.result
    val jobEvents = V8Schema.jobEvents.result

    def engineJobV8toV11_1(j: (Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String, Option[String])): (Int, UUID, String, String, JodaDateTime, JodaDateTime, String, String, String, String, Option[String]) =
      j.copy(_7 = AnalysisJobStates.intToState(j._7).getOrElse(AnalysisJobStates.UNKNOWN).toString)

    def jobEventV8toV11_1(e: (UUID, Int, Int, String, JodaDateTime)): (UUID, Int, String, String, JodaDateTime) =
      e.copy(_3 = AnalysisJobStates.intToState(e._3).getOrElse(AnalysisJobStates.UNKNOWN).toString)

    db.run(engineJobs.zip(jobEvents).flatMap { data =>
      (InitialSchema.engineJobs.schema ++ InitialSchema.jobEvents.schema ++ InitialSchema.jobStates.schema).drop >>
        (V11_1Schema.engineJobs.schema ++ V11_1Schema.jobEvents.schema).create >>
        DBIO.seq(V11_1Schema.engineJobs ++= data._1.map(engineJobV8toV11_1), V11_1Schema.jobEvents ++= data._2.map(jobEventV8toV11_1))
    })
  }
}

object V8Schema extends PacBioDateTimeDatabaseFormat {
  class JobStatesT(tag: Tag) extends Table[(Int, String, String, JodaDateTime, JodaDateTime)](tag, "job_states") {

    def id: Rep[Int] = column[Int]("job_state_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, JodaDateTime, JodaDateTime)] = (id, name, description, createdAt, updatedAt)

  }

  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String, Option[String])](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Rep[UUID] = column[UUID]("uuid")

    // this is really comment
    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def stateId: Rep[Int] = column[Int]("state_id")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jsonSettings: Rep[String] = column[String]("json_settings")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String, Option[String])] = (id, uuid, name, pipelineId, createdAt, updatedAt, stateId, jobTypeId, path, jsonSettings, createdBy)
  }

  class JobEventsT(tag: Tag) extends Table[(UUID, Int, Int, String, JodaDateTime)](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def stateId: Rep[Int] = column[Int]("state_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(UUID, Int, Int, String, JodaDateTime)] = (id, jobId, stateId, message, createdAt)
  }

  lazy val jobStates = TableQuery[JobStatesT]
  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val jobEvents = TableQuery[JobEventsT]

}

object V11_1Schema extends PacBioDateTimeDatabaseFormat {

  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, JodaDateTime, JodaDateTime, String, String, String, String, Option[String])](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Rep[UUID] = column[UUID]("uuid")

    // this is really comment
    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def state: Rep[String] = column[String]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jsonSettings: Rep[String] = column[String]("json_settings")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, String, String, String, String, Option[String])] = (id, uuid, name, pipelineId, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy)
  }

  class JobEventsT(tag: Tag) extends Table[(UUID, Int, String, String, JodaDateTime)](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def state: Rep[String] = column[String]("state")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(UUID, Int, String, String, JodaDateTime)] = (id, jobId, state, message, createdAt)
  }

  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val jobEvents = TableQuery[JobEventsT]
}
