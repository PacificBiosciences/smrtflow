package com.pacbio.secondary.analysis.jobs

import java.nio.file.Path
import java.util.UUID
import java.net.URL

import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

object AnalysisJobStates {

  trait JobStates {
    val stateId: Int
  }

  trait Completed

  case object CREATED extends JobStates {
    val stateId = 1
  }

  case object SUBMITTED extends JobStates {
    val stateId = 2
  }

  case object RUNNING extends JobStates {
    val stateId = 3
  }

  case object TERMINATED extends JobStates with Completed {
    val stateId = 4
  }

  case object SUCCESSFUL extends JobStates with Completed {
    val stateId = 5
  }

  case object FAILED extends JobStates with Completed {
    val stateId = 6
  }

  case object UNKNOWN extends JobStates {
    val stateId = 7
  }

  // sugar
  val VALID_STATES = Seq(CREATED, SUBMITTED, RUNNING, TERMINATED, SUCCESSFUL, FAILED, UNKNOWN)

  val COMPLETED_STATES = Seq(TERMINATED, SUCCESSFUL, FAILED)

  def isCompleted(state: JobStates): Boolean = COMPLETED_STATES contains state

  def intToState(i: Int): Option[JobStates] = VALID_STATES.map(x => (x.stateId, x)).toMap.get(i)

  // This is NOT case sensitive
  def toState(s: String): Option[JobStates] = VALID_STATES.map(x => (x.toString.toLowerCase, x)).toMap.get(s.toLowerCase)

}

object JobModels {

  // Uses the pbsmrtpipe Task Id format (e.g., "pbsmrtpipe.tasks.my_task")
  // the 'id' is the short name
  case class JobTypeId(id: String) {
    def fullName = s"pbscala.job_types.$id"
  }

  trait JobResourceBase {
    val jobId: UUID
    val path: Path
    val state: AnalysisJobStates.JobStates
  }

  // This is a terrible name
  case class JobResource(jobId: UUID,
                         path: Path,
                         state: AnalysisJobStates.JobStates) extends JobResourceBase

  trait JobResult {
    val uuid: UUID
    val jobType: String
    val message: String
    val runTimeSec: Int
    val state: AnalysisJobStates.JobStates
    val host: String
  }

  // This needs to be fixed.
  case class ResultSuccess(uuid: UUID, jobType: String, message: String, runTimeSec: Int, state: AnalysisJobStates.JobStates, host: String) extends JobResult

  case class ResultFailed(uuid: UUID, jobType: String, message: String, runTimeSec: Int, state: AnalysisJobStates.JobStates, host: String) extends JobResult

  case class NoAvailableWorkError(message: String)

  // New Job Models
  case class RunnableJob(job: CoreJob, state: AnalysisJobStates.JobStates)

  // There's a bit of awkwardness here due to the PrimaryKey of the job having a Int and UUID
  case class RunnableJobWithId(id: Int, job: CoreJob, state: AnalysisJobStates.JobStates)

  // This should only have the "completed" jobOptions states
  case class JobCompletedResult(uuid: UUID, state: AnalysisJobStates.JobStates)

  case class JobEvent(eventId: UUID,
                      jobId: Int,
                      state: AnalysisJobStates.JobStates,
                      message: String,
                      createdAt: JodaDateTime)

  // Generic Container for a "Job"
  // id is system locally unique human friendly id,
  // whereas UUID is the globally unique id
  // The jsonSettings are the Json serialized settings of the Job Options
  // This is used to persist to the db. Not completely thrilled with this.
  case class EngineJob(id: Int,
                       uuid: UUID,
                       name: String,
                       comment: String,
                       createdAt: JodaDateTime,
                       updatedAt: JodaDateTime,
                       state: AnalysisJobStates.JobStates,
                       jobTypeId: String,
                       path: String,
                       jsonSettings: String,
                       createdBy: Option[String]) {

      def apply(id: Int,
                uuid: UUID,
                name: String,
                comment: String,
                createdAt: JodaDateTime,
                updatedAt: JodaDateTime,
                stateId: Int,
                jobTypeId: String,
                path: String,
                jsonSettings: String,
                createdBy: Option[String]) = {

          // This might not be the best idea.
          val state = AnalysisJobStates.intToState(stateId) getOrElse AnalysisJobStates.UNKNOWN

          EngineJob(id, uuid, name, comment, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy)
      }
  }


  // This is too pbsmtpipe-centric. This should be generalized or defined a base trait
  case class AnalysisJobResources(path: Path,
                                  tasksDir: Path,
                                  workflowDir: Path,
                                  logDir: Path,
                                  htmlDir: Path,
                                  datastoreJson: Path,
                                  entryPointsJson: Path,
                                  jobReportJson: Path)

  trait ImportAble

  /**
    * Core DataStore File
    *
    * @param uniqueId    UUID of the DataStore file
    * @param sourceId    General string to provide context of it's origin
    * @param fileTypeId  FileType Id (FIXME, this should be a FileTypes.FileType)
    * @param fileSize    Size of the file
    * @param createdAt   created at time of the file
    * @param modifiedAt  modified timestamp of the file
    * @param path        Absolute path to the file
    * @param isChunked   Was the file an intermediate "chunked" file that was generated.
    * @param name        Display Name of the File
    * @param description Description of the File
    */
  case class DataStoreFile(uniqueId: UUID,
                           sourceId: String,
                           fileTypeId: String,
                           fileSize: Long,
                           createdAt: JodaDateTime,
                           modifiedAt: JodaDateTime,
                           path: String,
                           isChunked: Boolean = false,
                           name: String,
                           description: String) extends ImportAble

  // Container for file created from a Job
  case class DataStoreJobFile(jobId: UUID,
                              dataStoreFile: DataStoreFile)



  case class PacBioDataStore(createdAt: JodaDateTime, updatedAt: JodaDateTime, version: String, files: Seq[DataStoreFile]) extends ImportAble

  // Should think about making this a Path
  case class BoundEntryPoint(entryId: String, path: String)

  // Used in pipeline Templates. Name is the display name
  case class EntryPoint(entryId: String, fileTypeId: String, name: String)

  trait PipelineBaseOption {
    type In
    val id: String
    val name: String
    val value: In
    val description: String

    def pbOptionId: String // PacBio Option type
  }

  trait PipelineIntOptionBase extends PipelineBaseOption {
    type In = Int
  }

  trait PipelineDoubleOptionBase extends PipelineBaseOption {
    type In = Double
  }

  trait PipelineStrOptionBase extends PipelineBaseOption {
    type In = String
  }

  trait PipelineBooleanOptionBase extends PipelineBaseOption {
    type In = Boolean
  }

  //The option types are currently defined in pbsmrtpipe.contants which should
  // be migrated to pbcommand
  private def toI(sx: String) = s"pbsmrtpipe.option_types.$sx"

  case class PipelineStrOption(id: String, name: String, value: String, description: String) extends PipelineStrOptionBase {
    def pbOptionId = toI("string")
  }


  case class PipelineIntOption(id: String, name: String, value: Int, description: String) extends PipelineIntOptionBase {
    def pbOptionId = toI("integer")
  }

  case class PipelineDoubleOption(id: String, name: String, value: Double, description: String) extends PipelineDoubleOptionBase {
    def pbOptionId = toI("float")
  }

  case class PipelineBooleanOption(id: String, name: String, value: Boolean, description: String) extends PipelineBooleanOptionBase {
    def pbOptionId = toI("boolean")
  }

  // pbsmrtpipe/smrtflow Pipelines
  case class PipelineTemplate(id: String,
                              name: String,
                              description: String,
                              version: String,
                              options: Seq[PipelineBaseOption],
                              taskOptions: Seq[PipelineBaseOption],
                              entryPoints: Seq[EntryPoint],
                              tags: Seq[String], presets: Seq[PipelineTemplatePreset])

  // templateId refers to the PipelineTemplate Id
  case class PipelineTemplatePreset(presetId: String,
                                    templateId: String,
                                    options: Seq[PipelineBaseOption],
                                    taskOptions: Seq[PipelineBaseOption])


  // View Rules Models
  case class PipelineOptionViewRule(id: String, hidden: Boolean)

  case class PipelineTemplateViewRule(id: String, name: String, description: String, taskOptions: Seq[PipelineOptionViewRule])

}
