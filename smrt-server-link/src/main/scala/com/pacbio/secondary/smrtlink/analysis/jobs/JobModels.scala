package com.pacbio.secondary.smrtlink.analysis.jobs

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
  val FAILURE_STATES = Seq(TERMINATED, FAILED)

  def isCompleted(state: JobStates): Boolean = COMPLETED_STATES contains state

  def isSuccessful(state: JobStates): Boolean = state == SUCCESSFUL

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
  // XXX this is a little sloppy, as internally we use Double
  case object FLOAT extends OptionType { val optionTypeId = "float" }
  case object BOOL extends OptionType { val optionTypeId = "boolean" }
  case object CHOICE extends OptionType { val optionTypeId = "choice_string" }
  case object CHOICE_INT extends OptionType { val optionTypeId = "choice_integer" }
  case object CHOICE_FLOAT extends OptionType { val optionTypeId = "choice_float" }

}

object JobModels {

  // Constants used across all Job types

  object JobConstants {

    // Default Output job files
    val JOB_STDERR = "pbscala-job.stderr"
    val JOB_STDOUT = "pbscala-job.stdout"

    // This is the DataStore File "master" log. The fundamental log file for the
    // job should be stored here and added to the datastore for downstream consumers
    val DATASTORE_FILE_MASTER_LOG_ID = "pbsmrtpipe::master.log"

    // Event that means the Job state has been changed
    val EVENT_TYPE_JOB_STATUS = "smrtlink_job_status"
    // Event that means the Job Task has changed state
    val EVENT_TYPE_JOB_TASK_STATUS = "smrtlink_job_task_status"

    // Default project ID; all datasets that aren't
    // in more specific projects get this ID
    val GENERAL_PROJECT_ID = 1
  }

  object JobTypeIds {
    val CONVERT_FASTA_BARCODES = JobTypeId("convert-fasta-barcodes")
    val CONVERT_FASTA_REFERENCE = JobTypeId("convert-fasta-reference")
    val CONVERT_RS_MOVIE = JobTypeId("convert-rs-movie")
    val DELETE_DATASETS = JobTypeId("delete-datasets")
    val DELETE_JOB = JobTypeId("delete-job")
    val EXPORT_DATASETS = JobTypeId("export-datasets")
    val IMPORT_DATASET = JobTypeId("import-dataset")
    val IMPORT_DATASTORE = JobTypeId("import-datastore")
    val MERGE_DATASETS = JobTypeId("merge-datasets")
    val MOCK_PBSMRTPIPE = JobTypeId("mock-pbsmrtpipe")
    val PBSMRTPIPE = JobTypeId("pbsmrtpipe")
    val PBSMRTPIPE_DIRECT = JobTypeId("pbsmrtpipe-direct")
    val SIMPLE = JobTypeId("simple")
    val TS_JOB = JobTypeId("tech-support-job")
    val TS_SYSTEM_STATUS = JobTypeId("tech-support-status")
    val DB_BACKUP = JobTypeId("db-backup")

    val ALL = Seq(CONVERT_FASTA_BARCODES, CONVERT_FASTA_REFERENCE,
                  CONVERT_RS_MOVIE, DELETE_DATASETS, DELETE_JOB,
                  EXPORT_DATASETS, IMPORT_DATASET, IMPORT_DATASTORE,
                  MERGE_DATASETS, MOCK_PBSMRTPIPE, PBSMRTPIPE,
                  PBSMRTPIPE_DIRECT, SIMPLE, TS_JOB, TS_SYSTEM_STATUS, DB_BACKUP)

    def fromString(s: String) = ALL.map(x => (x.id, x)).toMap.get(s)

    // Job Types that require minimal memory and cpu resources and
    // run very quickly. Approximately 1-2 minutes.
    val QUICK_JOB_TYPES: Set[JobTypeId] = Set(
      CONVERT_FASTA_BARCODES,
      IMPORT_DATASET, MERGE_DATASETS,
      MOCK_PBSMRTPIPE, SIMPLE,
      TS_JOB, TS_SYSTEM_STATUS,
      DB_BACKUP,
      JobTypeId("import_dataset"), // These are backward compatible slop for naming
      JobTypeId("merge_dataset")  // inconsistencies
    )

  }

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


  /**
    * General Job Event data model.
    *
    * This can communicate finer granularity of
    *
    * @param eventId     Globally Unique id for the event.
    * @param jobId       Job Id
    * @param state       state of computational unit (either EngineJob or JobTask)
    * @param message     status message of event
    * @param createdAt   When the message was created
    * @param eventTypeId event type id
    */
  case class JobEvent(
      eventId: UUID,
      jobId: Int,
      state: AnalysisJobStates.JobStates,
      message: String,
      createdAt: JodaDateTime,
      eventTypeId: String = JobConstants.EVENT_TYPE_JOB_STATUS)


  /**
    * Core "Engine" Job data model
    *
    * Note, the jobTypeId should map to a unique schema for the jsonSettings provided.
    * For example, for jobTypeId, "pbsmrtpipe", the jsonSettings JSON must have a pipelineId key
    * that is a String.
    *
    *
    * @param id id of the Job (unique relative to the SL System)
    * @param uuid Globally unique job identifier
    * @param name Display name of task
    * @param comment User comment
    * @param createdAt when the Job was created
    * @param updatedAt when the job was last updated
    * @param state current state of the job
    * @param projectId id of the associated project
    * @param jobTypeId job type id
    * @param path path to job output directory
    * @param jsonSettings JSON format of the job options (this structure will be consistent with the job type id)
    * @param createdBy user that created the Job
    * @param smrtlinkVersion SL System version
    * @param isActive if the job is active. Not Active jobs will not be displayed by default
    * @param errorMessage error message if the job is an Error state.
    */
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
      createdByEmail: Option[String],
      smrtlinkVersion: Option[String],
      isActive: Boolean = true,
      errorMessage: Option[String] = None,
      projectId: Int = JobConstants.GENERAL_PROJECT_ID) {

      def isComplete: Boolean = AnalysisJobStates.isCompleted(this.state)
      def isSuccessful: Boolean = this.state == AnalysisJobStates.SUCCESSFUL
      def isRunning: Boolean = this.state == AnalysisJobStates.RUNNING
      def hasFailed: Boolean = AnalysisJobStates.FAILURE_STATES contains(this.state)

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
          createdByEmail: Option[String],
          smrtlinkVersion: Option[String],
          isActive: Boolean = true,
          errorMessage: Option[String] = None,
          projectId: Int = 1) = {

          // This might not be the best idea.
          val state = AnalysisJobStates.intToState(stateId) getOrElse AnalysisJobStates.UNKNOWN

          EngineJob(id, uuid, name, comment, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy,  createdByEmail, smrtlinkVersion, isActive, errorMessage, projectId)
      }
  }

  /**
    * A Single Engine Job has many sub-computational units of work. These are a JobTask.
    *
    * @param uuid         Globally unique id of the Task
    * @param jobId        Job Id
    * @param taskId       task id (unique in the context of a single job)
    * @param taskTypeId   Tool Contract Id
    * @param name         Display name of task
    * @param state        state of the Task
    * @param createdAt    when the task was created (Note, this is not necessarily when the task started to run
    * @param updatedAt    last time the task state was updated
    * @param errorMessage error message (if state in error state)
    */
  case class JobTask(uuid: UUID,
                     jobId: Int,
                     taskId: String,
                     taskTypeId: String,
                     name: String,
                     state: String,
                     createdAt: JodaDateTime,
                     updatedAt: JodaDateTime,
                     errorMessage: Option[String])

  /**
    * Update the state and status message of Task of a Job. If the task is in
    * the error state, the detailed error message will be propagated.
    *
    * @param jobId        Job id the task belongs to
    * @param uuid         Globally unique Task identifier
    * @param state        state of the Task (this needs to be consistent with the allowed analysis job states)
    * @param message      status Message
    * @param errorMessage Detailed Error Message of Job Task
    */
  case class UpdateJobTask(jobId: Int,
                           uuid: UUID,
                           state: String,
                           message: String,
                           errorMessage: Option[String])

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

  trait ImportAble {
    def summary: String
  }

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

    def summary: String = toString
  }

  // Container for file created from a Job.
  // MK. What is the purpose of this container?
  case class DataStoreJobFile(
      jobId: UUID,
      dataStoreFile: DataStoreFile)



  case class PacBioDataStore(createdAt: JodaDateTime, updatedAt: JodaDateTime, version: String, files: Seq[DataStoreFile]) extends ImportAble {
    override def summary = {
      s"PacBioDataStore Summary ${files.length} files Created at $createdAt Schema version $version\n" +
          files.zipWithIndex.map {case (d, i) => s"${i + 1}. ${d.toString}" }.reduce(_ + "\n" + _)
    }
  }

  // Should think about making this a Path
  case class BoundEntryPoint(entryId: String, path: String)

  // Used in pipeline Templates. Name is the display name
  case class EntryPoint(entryId: String, fileTypeId: String, name: String)

  trait PacBioBaseOption {
    type In
    val id: String
    val value: In
    val optionTypeId: String
  }

  trait PipelineBaseOption extends PacBioBaseOption {
    val name: String
    val description: String

    def asServiceOption: ServiceTaskOptionBase
  }

  trait PipelineChoiceOption extends PipelineBaseOption {
    val choices: Seq[In]
    def validate(v: In): In = {
      if (choices.toSet contains v) v
      else throw new UnsupportedOperationException(s"Value $v is not an allowed choice")
    }
    def applyValue(v: In): PipelineChoiceOption
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
    val optionTypeId = OptionTypes.STR.optionTypeId
    def asServiceOption = ServiceTaskStrOption(id, value, optionTypeId)
  }


  case class PipelineIntOption(id: String, name: String, value: Int, description: String) extends PipelineIntOptionBase {
    val optionTypeId = OptionTypes.INT.optionTypeId
    def asServiceOption = ServiceTaskIntOption(id, value, optionTypeId)
  }

  case class PipelineDoubleOption(id: String, name: String, value: Double, description: String) extends PipelineDoubleOptionBase {
    val optionTypeId = OptionTypes.FLOAT.optionTypeId
    def asServiceOption = ServiceTaskDoubleOption(id, value, optionTypeId)
  }

  case class PipelineBooleanOption(id: String, name: String, value: Boolean, description: String) extends PipelineBooleanOptionBase {
    val optionTypeId = OptionTypes.BOOL.optionTypeId
    def asServiceOption = ServiceTaskBooleanOption(id, value, optionTypeId)
  }

  case class PipelineChoiceStrOption(id: String, name: String, value: String, description: String, choices: Seq[String]) extends PipelineStrOptionBase with PipelineChoiceOption {
    val optionTypeId = OptionTypes.CHOICE.optionTypeId
    val _ = validate(value)

    def applyValue(v: String): PipelineChoiceStrOption = copy(value=validate(v))
    def asServiceOption = ServiceTaskStrOption(id, value, optionTypeId)
  }

  case class PipelineChoiceIntOption(id: String, name: String, value: Int, description: String, choices: Seq[Int]) extends PipelineIntOptionBase with PipelineChoiceOption {
    val optionTypeId = OptionTypes.CHOICE_INT.optionTypeId
    val _ = validate(value)

    def applyValue(v: Int): PipelineChoiceIntOption = copy(value=validate(v))
    def asServiceOption = ServiceTaskIntOption(id, value, optionTypeId)
  }

  case class PipelineChoiceDoubleOption(id: String, name: String, value: Double, description: String, choices: Seq[Double]) extends PipelineDoubleOptionBase with PipelineChoiceOption {
    val optionTypeId = OptionTypes.CHOICE_FLOAT.optionTypeId
    val _ = validate(value)

    def applyValue(v: Double): PipelineChoiceDoubleOption = copy(value=validate(v))
    def asServiceOption = ServiceTaskDoubleOption(id, value, optionTypeId)
  }

  trait ServiceTaskOptionBase extends PacBioBaseOption {
  }

  trait ServiceTaskStrOptionBase extends ServiceTaskOptionBase{ type In = String }
  trait ServiceTaskIntOptionBase extends ServiceTaskOptionBase{ type In = Int }
  trait ServiceTaskBooleanOptionBase extends ServiceTaskOptionBase{ type In = Boolean }
  trait ServiceTaskDoubleOptionBase extends ServiceTaskOptionBase{ type In = Double }

  case class ServiceTaskStrOption(id: String, value: String, optionTypeId: String = OptionTypes.STR.optionTypeId) extends ServiceTaskStrOptionBase
  case class ServiceTaskIntOption(id: String, value: Int, optionTypeId: String = OptionTypes.INT.optionTypeId) extends ServiceTaskIntOptionBase
  case class ServiceTaskBooleanOption(id: String, value: Boolean, optionTypeId: String = OptionTypes.BOOL.optionTypeId) extends ServiceTaskBooleanOptionBase
  case class ServiceTaskDoubleOption(id: String, value: Double, optionTypeId: String = OptionTypes.FLOAT.optionTypeId) extends ServiceTaskDoubleOptionBase

  // Raw (aka) Direct Options. Minimal options used to call pbsmrtpipe
  case class PbsmrtpipeDirectJobOptions(
      projectId: Int = 1,
      pipelineId: String,
      entryPoints: Seq[BoundEntryPoint],
      taskOptions: Seq[ServiceTaskOptionBase],
      workflowOptions: Seq[ServiceTaskOptionBase])

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
      pipelineId: String,
      options: Seq[ServiceTaskOptionBase],
      taskOptions: Seq[ServiceTaskOptionBase])


  // View Rules Models
  case class PipelineOptionViewRule(id: String, hidden: Boolean, advanced: Boolean)

  case class PipelineTemplateViewRule(
      id: String,
      name: String,
      description: String,
      taskOptions: Seq[PipelineOptionViewRule])

  // FIXME(mkocher)(2016-8-18) All of these View rules should probable be migrated to a central location
  case class DataStoreFileViewRule(
      sourceId: String,
      fileTypeId: String,
      isHidden: Boolean,
      name: Option[String],
      description: Option[String])
  case class PipelineDataStoreViewRules(
      pipelineId: String,
      rules: Seq[DataStoreFileViewRule],
      smrtlinkVersion: String)

  case class MigrationStatusRow(timestamp: String, success: Boolean, error: Option[String] = None)


  // Tech Support Related Models. These really belong on "common"

  object BundleTypes {
    // Given that these bundle type ids are translated to eventTypeIds,
    // these need to be prefixed with "sl" to be consistent with
    // the SL System Event eventTypeId convention (for ElasticSearch motivations)
    val FAILED_INSTALL = "sl_ts_bundle_failed_install"
    val SYSTEM_STATUS = "sl_ts_bundle_system_status"
    val JOB = "sl_ts_bundle_job"
    // Should only be used in unittests
    val TEST = "sl_ts_test_bundle"
  }

  trait TsManifest {
    val id: UUID
    val bundleTypeId: String
    val bundleTypeVersion: Int
    val createdAt: JodaDateTime
    val smrtLinkSystemId: UUID
    val smrtLinkSystemVersion: Option[String]
    val user: String
    val comment: Option[String]
    // I don't like dragging this around. This shouldn't be used as a primary key
    val dnsName: Option[String]

  }

  /**
    * Tech Support metadata "Bundle"
    *
    * General Tech Support Manifest
    *
    * The bundle type id should map to well defined schema of files within the tgz.
    * When that schema changes, the bundle type version should be incremented.
    *
    * @param id                    Globally unique if of the bundle
    * @param bundleTypeId          Bundle type id (e.g, "failed_smrtlink_install", "failed_smrtlink_analysis_job"
    * @param bundleTypeVersion     Version of this Schema
    * @param createdAt             When the bundle was created
    * @param smrtLinkSystemId      Unique id of the SL System
    * @param smrtLinkSystemVersion SL System Version
    * @param user                  User who created the bundle
    */
  case class TsSystemStatusManifest(id: UUID,
                                    bundleTypeId: String,
                                    bundleTypeVersion: Int,
                                    createdAt: JodaDateTime,
                                    smrtLinkSystemId: UUID,
                                    dnsName: Option[String],
                                    smrtLinkSystemVersion: Option[String],
                                    user: String,
                                    comment: Option[String]) extends TsManifest

  case class TsJobManifest(id: UUID,
                           bundleTypeId: String,
                           bundleTypeVersion: Int,
                           createdAt: JodaDateTime,
                           smrtLinkSystemId: UUID,
                           dnsName: Option[String],
                           smrtLinkSystemVersion: Option[String],
                           user: String,
                           comment: Option[String],
                           jobTypeId: String,
                           jobId: Int) extends TsManifest



}
