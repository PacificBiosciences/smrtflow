package com.pacbio.secondary.analysis.jobs

import java.nio.file.{Path, Paths}
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

// mirrors pbcommand.models.common.TaskOptionTypes
object OptionTypes {
  sealed trait OptionType {
    val optionTypeId: String
  }

  case object STR extends OptionType { val optionTypeId = "string" }
  case object INT extends OptionType { val optionTypeId = "integer" }
  case object FLOAT extends OptionType { val optionTypeId = "float" }
  case object BOOL extends OptionType { val optionTypeId = "boolean" }
  case object CHOICE extends OptionType { val optionTypeId = "choice_string" }
  case object CHOICE_INT extends OptionType { val optionTypeId = "choice_integer" }
  case object CHOICE_FLOAT extends OptionType { val optionTypeId = "choice_float" }
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
  case class JobResource(
      jobId: UUID,
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

  case class JobEvent(
      eventId: UUID,
      jobId: Int,
      state: AnalysisJobStates.JobStates,
      message: String,
      createdAt: JodaDateTime)

  // Generic Container for a "Job"
  // id is system locally unique human friendly id,
  // whereas UUID is the globally unique id
  // The jsonSettings are the Json serialized settings of the Job Options
  // This is used to persist to the db. Not completely thrilled with this.
  case class EngineJob(
      id: Int,
      uuid: UUID,
      name: String,
      comment: String,
      createdAt: JodaDateTime,
      updatedAt: JodaDateTime,
      state: AnalysisJobStates.JobStates,
      jobTypeId: String,
      path: String,
      jsonSettings: String,
      createdBy: Option[String],
      smrtlinkVersion: Option[String],
      smrtlinkToolsVersion: Option[String],
      isActive: Boolean = true) {

      def isComplete: Boolean = AnalysisJobStates.isCompleted(this.state)
      def isSuccessful: Boolean = this.state == AnalysisJobStates.SUCCESSFUL
      def isRunning: Boolean = this.state == AnalysisJobStates.RUNNING

      def apply(
          id: Int,
          uuid: UUID,
          name: String,
          comment: String,
          createdAt: JodaDateTime,
          updatedAt: JodaDateTime,
          stateId: Int,
          jobTypeId: String,
          path: String,
          jsonSettings: String,
          createdBy: Option[String],
          smrtlinkVersion: Option[String],
          smrtlinkToolsVersion: Option[String],
          isActive: Boolean = true) = {

          // This might not be the best idea.
          val state = AnalysisJobStates.intToState(stateId) getOrElse AnalysisJobStates.UNKNOWN

          EngineJob(id, uuid, name, comment, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy, smrtlinkVersion, smrtlinkToolsVersion, isActive)
      }
  }


  // This is too pbsmtpipe-centric. This should be generalized or defined a base trait
  case class AnalysisJobResources(
      path: Path,
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
  case class DataStoreFile(
      uniqueId: UUID,
      sourceId: String,
      fileTypeId: String,
      fileSize: Long,
      createdAt: JodaDateTime,
      modifiedAt: JodaDateTime,
      path: String,
      isChunked: Boolean = false,
      name: String,
      description: String) extends ImportAble {

    def fileExists: Boolean = Paths.get(path).toFile.exists
  }

  // Container for file created from a Job
  case class DataStoreJobFile(
      jobId: UUID,
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
    val pbOption: OptionTypes.OptionType

    def pbOptionId: String = pbOption.optionTypeId // PacBio Option type
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

  case class PipelineStrOption(id: String, name: String, value: String, description: String) extends PipelineStrOptionBase {
    val pbOption = OptionTypes.STR
  }


  case class PipelineIntOption(id: String, name: String, value: Int, description: String) extends PipelineIntOptionBase {
    val pbOption = OptionTypes.INT
  }

  case class PipelineDoubleOption(id: String, name: String, value: Double, description: String) extends PipelineDoubleOptionBase {
    val pbOption = OptionTypes.FLOAT
  }

  case class PipelineBooleanOption(id: String, name: String, value: Boolean, description: String) extends PipelineBooleanOptionBase {
    val pbOption = OptionTypes.BOOL
  }

  case class PipelineChoiceStrOption(id: String, name: String, value: String, description: String, choices: Seq[String]) extends PipelineStrOptionBase {
    val pbOption = OptionTypes.CHOICE

    def applyValue(v: String) = {
      if (! (choices.toSet contains v)) throw new UnsupportedOperationException(s"Value $v is not an allowed choice")
      copy(value = v)
    }
  }

  case class PipelineChoiceIntOption(id: String, name: String, value: Int, description: String, choices: Seq[Int]) extends PipelineIntOptionBase {
    val pbOption = OptionTypes.CHOICE_INT

    def applyValue(v: Int) = {
      if (! (choices.toSet contains v)) throw new UnsupportedOperationException(s"Value $v is not an allowed choice")
      copy(value = v)
    }
  }

  case class PipelineChoiceDoubleOption(id: String, name: String, value: Double, description: String, choices: Seq[Double]) extends PipelineDoubleOptionBase {
    val pbOption = OptionTypes.CHOICE_FLOAT

    def applyValue(v: Double) = {
      if (! (choices.toSet contains v)) throw new UnsupportedOperationException(s"Value $v is not an allowed choice")
      copy(value = v)
    }
  }

  // Raw (aka) Direct Options. Minimal options used to call pbsmrtpipe
  case class PbsmrtpipeDirectJobOptions(
      pipelineId: String,
      entryPoints: Seq[BoundEntryPoint],
      taskOptions: Seq[PipelineBaseOption],
      workflowOptions: Seq[PipelineBaseOption])

  // pbsmrtpipe/smrtflow Pipelines
  case class PipelineTemplate(
      id: String,
      name: String,
      description: String,
      version: String,
      options: Seq[PipelineBaseOption],
      taskOptions: Seq[PipelineBaseOption],
      entryPoints: Seq[EntryPoint],
      tags: Seq[String],
      presets: Seq[PipelineTemplatePreset])

  // templateId refers to the PipelineTemplate Id
  case class PipelineTemplatePreset(
      presetId: String,
      templateId: String,
      options: Seq[PipelineBaseOption],
      taskOptions: Seq[PipelineBaseOption])


  // View Rules Models
  case class PipelineOptionViewRule(id: String, hidden: Boolean, advanced: Boolean)

  case class PipelineTemplateViewRule(
      id: String,
      name: String,
      description: String,
      taskOptions: Seq[PipelineOptionViewRule])

  // FIXME(mkocher)(2016-8-18) All of these View rules should probable be migrated to a central location
  case class DataStoreFileViewRule(sourceId: String, fileTypeId: String, isHidden: Boolean, name: Option[String], description: Option[String])
  case class PipelineDataStoreViewRules(pipelineId: String, rules: Seq[DataStoreFileViewRule], smrtlinkVersion: String)


  // Constants used across all Job types

  object JobConstants {

    // Default Output job files
    val JOB_STDERR = "pbscala-job.stderr"
    val JOB_STDOUT = "pbscala-job.stdout"

    // This is the DataStore File "master" log. The fundamental log file for the
    // job should be stored here and added to the datastore for downstream consumers
    val DATASTORE_FILE_MASTER_LOG_ID = "pbsmrtpipe::master.log"


  }

}
