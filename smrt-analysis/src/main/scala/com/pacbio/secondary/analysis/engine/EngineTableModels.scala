package com.pacbio.secondary.analysis.engine


import java.util.UUID

import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.H2Driver.api._
import com.github.tototoshi.slick.SQLiteJodaSupport._
import slick.jdbc.JdbcBackend
import slick.lifted.{ProvenShape, ForeignKeyQuery}
import slick.jdbc.meta.MTable
import scala.util.Random

/**
 * Models for SQL Engine DAO
 *
 * Created by mkocher on 9/2/15.
 */
object EngineTableModels {

  implicit val jobStateType = MappedColumnType.base[AnalysisJobStates.JobStates, Int](
    {s => s.stateId},
    {s => AnalysisJobStates.intToState(s).getOrElse(AnalysisJobStates.UNKNOWN)}
  )

  class JobEventsT(tag: Tag) extends Table[(Int, AnalysisJobStates.JobStates, Int, String, JodaDateTime)](tag, "job_events") {
    def id: Rep[Int] = column[Int]("job_event_id", O.PrimaryKey, O.AutoInc)

    def stateId: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def * : ProvenShape[(Int, AnalysisJobStates.JobStates, Int, String, JodaDateTime)] = (id, stateId, jobId, message, createdAt)
  }

  class JobTags(tag: Tag) extends Table[(Int, String)](tag, "job_tags") {
    def id: Rep[Int] = column[Int]("job_tag_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def * : ProvenShape[(Int, String)] = (id, name)
  }

  /**
   * Many-to-Many table for tags for Jobs
   * @param tag General Tags for Jobs
   */
  class JobsTags(tag: Tag) extends Table[(Int, Int)](tag, "jobs_tags") {
    def jobId: Rep[Int] = column[Int]("job_id")

    def tagId: Rep[Int] = column[Int]("job_tag_id")

    def * : ProvenShape[(Int, Int)] = (jobId, tagId)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def jobTagFK = foreignKey("job_tag_fk", tagId, jobTags)(a => a.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(b => b.id)
  }


  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, JodaDateTime, JodaDateTime, AnalysisJobStates.JobStates, String)](tag, "engine_jobs") {
    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Rep[UUID] = column[UUID]("uuid")

    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def stateId: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state_id")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, AnalysisJobStates.JobStates, String)] = (id, uuid, name, pipelineId, createdAt, updatedAt, stateId, jobTypeId)
  }

  class JobResultT(tag: Tag) extends Table[(Int, String)](tag, "job_results") {
    def id: Rep[Int] = column[Int]("job_result_id")

    def host: Rep[String] = column[String]("host_name")

    def jobId: Rep[Int] = column[Int]("job_id")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(Int, String)] = (id, host)
  }

  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val jobEvents = TableQuery[JobEventsT]
  lazy val jobTags = TableQuery[JobTags]
  lazy val jobsTags = TableQuery[JobsTags]
}
