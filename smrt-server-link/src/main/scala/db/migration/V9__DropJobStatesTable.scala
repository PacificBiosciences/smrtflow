package db.migration

import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}

import slick.driver.SQLiteDriver.api._
import slick.lifted.ProvenShape

class V9__DropJobStatesTable extends JdbcMigration with SlickMigration {
  override def slickMigrate: DBIOAction[Any, NoStream, Nothing] = {
    val engineJobs = InitialSchema.engineJobs.result
    val jobEvents = InitialSchema.jobEvents.result

    engineJobs.zip(jobEvents).flatMap { data =>
      (InitialSchema.engineJobs.schema ++ InitialSchema.jobEvents.schema ++ InitialSchema.jobStates.schema).drop >>
        (V9Schema.engineJobs.schema ++ V9Schema.jobEvents.schema).create >>
        DBIO.seq(V9Schema.engineJobs ++= data._1, V9Schema.jobEvents ++= data._2)
    }
  }
}

object V9Schema extends PacBioDateTimeDatabaseFormat {

  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String)](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Rep[UUID] = column[UUID]("uuid")

    // this is really comment
    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def state: Rep[Int] = column[Int]("state_id")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jsonSettings: Rep[String] = column[String]("json_settings")

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String)] = (id, uuid, name, pipelineId, createdAt, updatedAt, state, jobTypeId, path, jsonSettings)
  }

  class JobEventsT(tag: Tag) extends Table[(UUID, Int, Int, String, JodaDateTime)](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def state: Rep[Int] = column[Int]("state_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(UUID, Int, Int, String, JodaDateTime)] = (id, jobId, state, message, createdAt)
  }

  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val jobEvents = TableQuery[JobEventsT]
}
