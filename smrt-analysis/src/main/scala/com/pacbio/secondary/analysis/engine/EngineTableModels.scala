package com.pacbio.secondary.analysis.engine


import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import scala.slick.driver.SQLiteDriver.simple._
import com.github.tototoshi.slick.SQLiteJodaSupport._
import scala.slick.jdbc.JdbcBackend
import scala.slick.lifted.{ProvenShape, ForeignKeyQuery}
import slick.jdbc.meta.MTable
import scala.util.Random

/**
 * Models for SQL Engine DAO
 *
 * Created by mkocher on 9/2/15.
 */
object EngineTableModels {

  class JobStatesT(tag: Tag) extends Table[(Int, String, String, JodaDateTime, JodaDateTime)](tag, "job_states") {

    def id: Column[Int] = column[Int]("job_state_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def description: Column[String] = column[String]("description")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, JodaDateTime, JodaDateTime)] = (id, name, description, createdAt, updatedAt)

  }

  class JobEventsT(tag: Tag) extends Table[(Int, Int, Int, String, JodaDateTime)](tag, "job_events") {

    def id: Column[Int] = column[Int]("job_event_id", O.PrimaryKey, O.AutoInc)

    def stateId: Column[Int] = column[Int]("state_id")

    def jobId: Column[Int] = column[Int]("job_id")

    def message: Column[String] = column[String]("message")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def stateJoin = jobStates.filter(_.id === stateId)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def * : ProvenShape[(Int, Int, Int, String, JodaDateTime)] = (id, stateId, jobId, message, createdAt)
  }

  class JobTags(tag: Tag) extends Table[(Int, String)](tag, "job_tags") {
    def id: Column[Int] = column[Int]("job_tag_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def * : ProvenShape[(Int, String)] = (id, name)
  }

  /**
   * Many-to-Many table for tags for Jobs
   * @param tag General Tags for Jobs
   */
  class JobsTags(tag: Tag) extends Table[(Int, Int)](tag, "jobs_tags") {
    def jobId: Column[Int] = column[Int]("job_id")

    def tagId: Column[Int] = column[Int]("job_tag_id")

    def * : ProvenShape[(Int, Int)] = (jobId, tagId)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def jobTagFK = foreignKey("job_tag_fk", tagId, jobTags)(a => a.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(b => b.id)
  }


  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String)](tag, "engine_jobs") {

    def id: Column[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Column[UUID] = column[UUID]("uuid")

    def pipelineId: Column[String] = column[String]("pipeline_id")

    def name: Column[String] = column[String]("name")

    def stateId: Column[Int] = column[Int]("state_id")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def stateJoin = jobStates.filter(_.id === stateId)

    def jobTypeId: Column[String] = column[String]("job_type_id")

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String)] = (id, uuid, name, pipelineId, createdAt, updatedAt, stateId, jobTypeId)
  }

  class JobResultT(tag: Tag) extends Table[(Int, String)](tag, "job_results") {

    def id: Column[Int] = column[Int]("job_result_id")

    def host: Column[String] = column[String]("host_name")

    def jobId: Column[Int] = column[Int]("job_id")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(Int, String)] = (id, host)

  }

  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val jobEvents = TableQuery[JobEventsT]
  lazy val jobStates = TableQuery[JobStatesT]
  lazy val jobTags = TableQuery[JobTags]
  lazy val jobsTags = TableQuery[JobsTags]


  lazy val ddls = engineJobs.ddl ++ jobEvents.ddl ++ jobStates.ddl ++ jobTags.ddl ++ jobsTags.ddl
}
