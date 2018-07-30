package com.pacbio.secondary.smrtlink.analysis.jobs

import java.nio.file.{Path, Paths}
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import scala.concurrent.duration.{FiniteDuration, TimeUnit}

object AnalysisJobStates {

  // Changing this to a sealed trait creates ambigious implicit errors with spray
  trait JobStates {
    val stateId: Int
    def isCompleted: Boolean
  }

  case object CREATED extends JobStates {
    val stateId = 1
    override def isCompleted = false
  }

  case object SUBMITTED extends JobStates {
    val stateId = 2
    override def isCompleted = false
  }

  case object RUNNING extends JobStates {
    val stateId = 3

    override def isCompleted = false
  }

  case object TERMINATED extends JobStates {
    val stateId = 4

    override def isCompleted = true
  }

  case object SUCCESSFUL extends JobStates {
    val stateId = 5

    override def isCompleted = true
  }

  case object FAILED extends JobStates {
    val stateId = 6

    override def isCompleted = true
  }

  case object UNKNOWN extends JobStates {
    val stateId = 7

    override def isCompleted = false
  }

  // sugar
  val VALID_STATES =
    Seq(CREATED, SUBMITTED, RUNNING, TERMINATED, SUCCESSFUL, FAILED, UNKNOWN)

  val COMPLETED_STATES = Seq(TERMINATED, SUCCESSFUL, FAILED)
  val FAILURE_STATES = Seq(TERMINATED, FAILED)

  def isCompleted(state: JobStates): Boolean = COMPLETED_STATES contains state

  def notCompleted(state: JobStates): Boolean = !isCompleted(state)

  def isSuccessful(state: JobStates): Boolean = state == SUCCESSFUL

  def hasFailed(state: JobStates): Boolean = !isSuccessful(state)

  def intToState(i: Int): Option[JobStates] =
    VALID_STATES.map(x => (x.stateId, x)).toMap.get(i)

  // This is NOT case sensitive
  def toState(s: String): Option[JobStates] =
    VALID_STATES.map(x => (x.toString.toLowerCase, x)).toMap.get(s.toLowerCase)

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
  case object CHOICE_INT extends OptionType {
    val optionTypeId = "choice_integer"
  }
  case object CHOICE_FLOAT extends OptionType {
    val optionTypeId = "choice_float"
  }

}

case class InvalidJobOptionError(msg: String) extends Exception(msg)

object JobModels {

  // Constants used across all Job types

  object JobConstants {

    // Default Output job files
    val JOB_STDERR = "pbscala-job.stderr"
    val JOB_STDOUT = "pbscala-job.stdout"

    val OUTPUT_DATASTORE_JSON = "datastore.json"
    val OUTPUT_EXPORT_MANIFEST_JSON = "export-job-manifest.json"

    // This is the DataStore File "master" log. The fundamental log file for the
    // job should be stored here and added to the datastore for downstream consumers
    val DATASTORE_FILE_MASTER_LOG_ID = "pbsmrtpipe::master.log"

    val DATASTORE_FILE_MASTER_NAME = "SMRT Link Job Log"
    val DATASTORE_FILE_MASTER_DESC = "SMRT Link Job Log"

    // Event that means the Job state has been changed
    val EVENT_TYPE_JOB_STATUS = "smrtlink_job_status"
    // Event that means the Job Task has changed state
    val EVENT_TYPE_JOB_TASK_STATUS = "smrtlink_job_task_status"

    // Default project ID; all datasets that aren't
    // in more specific projects get this ID
    val GENERAL_PROJECT_ID = 1
    // THIS MUST BE GLOBALLY UNIQUE
    val GENERAL_PROJECT_NAME = "General Project"

    val SUBMIT_DEFAULT_CORE_JOB = true
    val SUBMIT_DEFAULT_MULTI_JOB = false

    val BARCODE_SET_MAX_NUM_RECORDS = 384
  }

  // This needs to be made a proper type
  object JobTypeIds {

    // This is done as sealed trait to leverage the compiler for JobType matching
    sealed trait JobType {
      // Note, "id" will be used as the job prefix in the URL and by convention use hypen to avoid the camelcase vs snakecase naming
      val id: String
      val name: String
      val description: String
      def isQuick: Boolean = false
      def isMultiJob: Boolean = false
    }

    case object HELLO_WORLD extends JobType {
      val id = "hello-world"
      val name = "Hello World"
      val description = "Sanity Test hello-world job"
      override def isQuick: Boolean = true
    }

    case object CONVERT_FASTA_BARCODES extends JobType {
      val id = "convert-fasta-barcodes"
      val name = "Convert Barcode Fasta"
      val description = "Convert Barcode Fasta file to BarcodeSet XML"
    }

    case object CONVERT_FASTA_REFERENCE extends JobType {
      val id = "convert-fasta-reference"
      val name = "Convert Fasta to ReferenceSet"
      val description = "Convert PacBio spec Fasta file to ReferenceSet XML"
    }

    case object CONVERT_FASTA_GMAPREFERENCE extends JobType {
      val id = "convert-fasta-gmapreference"
      val name = "Convert Fasta to GmapReferenceSet"
      val description =
        "Convert PacBio spec Fasta file to GmapReferenceSet XML"
    }

    case object CONVERT_RS_MOVIE extends JobType {
      val id = "convert-rs-movie"
      val name = "Convert RS to HdfSubreadSet"
      val description =
        "Convert a Legacy RS movie XML file to HdfSubreadSet XML"
      override def isQuick: Boolean = true
    }

    case object DELETE_DATASETS extends JobType {
      val id = "delete-datasets"
      val name = "Delete DataSet"
      val description = "(Soft) delete of PacBio DataSet XML"
      override def isQuick: Boolean = true
    }

    case object DELETE_JOB extends JobType {
      val id = "delete-job"
      val name = "Delete Job"
      val description = "(Soft) Delete of a SMRT Link Job"
      override def isQuick: Boolean = true
    }

    case object EXPORT_DATASETS extends JobType {
      val id = "export-datasets"
      val name = "Export DataSet"
      val description = "Export DataSet XML(s) as zip"
      override def isQuick: Boolean = true
    }

    case object EXPORT_JOBS extends JobType {
      val id = "export-jobs"
      val name = "Export Jobs"
      val description = "Export Job(s) as zip file(s)"
      override def isQuick: Boolean = false
    }

    case object IMPORT_JOB extends JobType {
      val id = "import-job"
      val name = "Import Job"
      val description = "Import SMRT Link job from ZIP file"
      override def isQuick: Boolean = false
    }

    case object IMPORT_DATASET extends JobType {
      val id = "import-dataset"
      val name = "Import PacBio DataSet"
      val description = "Import a PacBio XML DataSet"
      override def isQuick: Boolean = true
    }

    case object IMPORT_DATASETS_ZIP extends JobType {
      val id = "import-datasets-zip"
      val name = "Import ZIP of PacBio DataSet(s)"
      val description = "Import a ZIP of PacBio XML DataSet(s)"
      override def isQuick: Boolean = true
    }

    case object MERGE_DATASETS extends JobType {
      val id = "merge-datasets"
      val name = "Merge PacBio DataSet(s)"
      val description =
        "Merge DatatSet(s) Only SubreadSet, HdfSubreadSet dataset types are supported"
      override def isQuick: Boolean = true
    }

    case object MOCK_PBSMRTPIPE extends JobType {
      val id = "mock-pbsmrtpipe"
      val name = "Mock Pbsmrtpipe Job"
      val description = "Mock Pbsmrtpipe for testing"
      override def isQuick: Boolean = true
    }

    case object PBSMRTPIPE extends JobType {
      val id = "pbsmrtpipe"
      val name = "Pbsmrtpipe"
      val description = "Pbsmrtpipe (i.e., analysis) Jobs"
    }

    case object SIMPLE extends JobType {
      val id = "simple"
      val name = "Simple"
      val description = "Simple Job type for testing"
      override def isQuick: Boolean = true
    }

    case object TS_JOB extends JobType {
      val id = "tech-support-job"
      val name = "PacBio TechSupport Failed Job"
      val description = "Create a TechSupport TGZ bundle from a failed job"
      override def isQuick: Boolean = true
    }

    case object TS_SYSTEM_STATUS extends JobType {
      val id = "tech-support-status"
      val name = "PacBio Tech Support System Status"
      val description = "Create a TechSupport system status TGZ bundle"
      override def isQuick: Boolean = true
    }

    case object TS_JOB_HARVESTER_JOB extends JobType {
      val id = "tech-support-job-harvester"
      val name = "PacBio Job Harvester"
      val description =
        "Export All Analysis Jobs and upload to PacBio as a TS TGZ bundle"
      override def isQuick: Boolean = true
    }

    case object DB_BACKUP extends JobType {
      val id = "db-backup"
      val name = "SMRT Link db backup"
      val description = "Create a DB backup of the SMRT Link system"
      override def isQuick: Boolean = true
    }

    case object DS_COPY extends JobType {
      val id = "copy-dataset"
      val name = "DataSet copying and filtering"
      val description = "Copy an XML dataset, with optional filters"
      override def isQuick: Boolean = true
    }

    case object MJOB_MULTI_ANALYSIS extends JobType {
      val id = "multi-analysis"
      val name = "Run Multi-Analysis Jobs"
      val description =
        """General multi-job for wait for the Entry Points for a list of N jobs to resolved,
          |then create N analysis/pbsmrtpipe jobs and poll for terminal states of each job""".stripMargin
      override def isMultiJob: Boolean = true
    }

    // This really shouldn't be private
    val ALL = Seq(
      CONVERT_FASTA_BARCODES,
      CONVERT_FASTA_REFERENCE,
      CONVERT_FASTA_GMAPREFERENCE,
      CONVERT_RS_MOVIE,
      DELETE_DATASETS,
      DELETE_JOB,
      EXPORT_DATASETS,
      IMPORT_DATASET,
      IMPORT_DATASETS_ZIP,
      EXPORT_JOBS,
      IMPORT_JOB,
      MERGE_DATASETS,
      MOCK_PBSMRTPIPE,
      PBSMRTPIPE,
      SIMPLE,
      TS_JOB,
      TS_SYSTEM_STATUS,
      TS_JOB_HARVESTER_JOB,
      DB_BACKUP,
      DS_COPY,
      MJOB_MULTI_ANALYSIS
    )

    def fromString(s: String): Option[JobType] =
      ALL.map(x => (x.id.toLowerCase(), x)).toMap.get(s.toLowerCase)
  }

  trait JobResourceBase {
    val jobId: UUID
    val path: Path
  }

  // This is a terrible name
  case class JobResource(jobId: UUID, path: Path) extends JobResourceBase

  trait JobResult {
    val uuid: UUID
    val jobType: String
    val message: String
    val runTimeSec: Int
    val state: AnalysisJobStates.JobStates
    val host: String
  }

  // This needs to be fixed.
  case class ResultSuccess(uuid: UUID,
                           jobType: String,
                           message: String,
                           runTimeSec: Int,
                           state: AnalysisJobStates.JobStates,
                           host: String)
      extends JobResult

  // On Failed Results, and datastore files in the datastore will also be imported
  case class ResultFailed(uuid: UUID,
                          jobType: String,
                          message: String,
                          runTimeSec: Int,
                          state: AnalysisJobStates.JobStates,
                          host: String,
                          datastore: Option[PacBioDataStore] = None)
      extends JobResult

  case class NoAvailableWorkError(message: String)

  case class EngineManagerStatus(totalGeneralWorkers: Int,
                                 activeGeneralWorkers: Int,
                                 totalQuickWorkers: Int,
                                 activeQuickWorkers: Int) {
    def prettySummary =
      s"GeneralWorkers active/total ($activeGeneralWorkers/$totalGeneralWorkers) QuickWorkers active/total $activeQuickWorkers/$totalQuickWorkers"
  }

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
  case class JobEvent(eventId: UUID,
                      jobId: Int,
                      state: AnalysisJobStates.JobStates,
                      message: String,
                      createdAt: JodaDateTime,
                      eventTypeId: String = JobConstants.EVENT_TYPE_JOB_STATUS)

  trait SmrtLinkJob {
    val id: Int
    val name: String
    val comment: String
    val createdAt: JodaDateTime
    val updatedAt: JodaDateTime
    val state: AnalysisJobStates.JobStates
    val jobTypeId: String
    val subJobTypeId: Option[String]
    val path: String
    val jsonSettings: String
    val createdBy: Option[String]
    val createdByEmail: Option[String]
    val smrtlinkVersion: Option[String]
    val isActive: Boolean
    val errorMessage: Option[String]
    val projectId: Int

    def isComplete: Boolean = AnalysisJobStates.isCompleted(state)
    def isSuccessful: Boolean = state == AnalysisJobStates.SUCCESSFUL
    def isRunning: Boolean = state == AnalysisJobStates.RUNNING
    def hasFailed: Boolean = AnalysisJobStates.FAILURE_STATES contains (state)
  }

  /**
    * Core "Engine" Job data model
    *
    * Note, the jobTypeId should map to a unique schema for the jsonSettings provided.
    * For example, for jobTypeId, "pbsmrtpipe", the jsonSettings JSON must have a pipelineId key
    * that is a String.
    *
    * @param id              id of the Job (unique relative to the SL System)
    * @param uuid            Globally unique job identifier
    * @param name            Display name of task
    * @param comment         User comment
    * @param createdAt       when the Job was created
    * @param updatedAt       when the job metadata was last updated at
    * @param jobUpdatedAt    when the job execution was STATE was last updated at
    * @param state           current state of the job
    * @param projectId       id of the associated project
    * @param jobTypeId       job type id
    * @param path            path to job output directory
    * @param jsonSettings    JSON format of the job options (this structure will be consistent with the job type id)
    * @param createdBy       user that created the Job
    * @param smrtlinkVersion SL System version
    * @param isActive        if the job is active. Not Active jobs will not be displayed by default
    * @param errorMessage    error message if the job is an Error state.
    * @param importedAt      if the job was imported, this will be the timestamp of the imported at date
    * @param tags            tags of jobs. This follows the same model as the dataset. An empty string is the default and
    *                        values are comma separated.
    * @param subJobTypeId    The Job "sub job type id". This could be used to communicate a specific job type of the "root"
    *                        jobTypeId. For pbsmrtpipe/analysis jobs, the pipeline id is used.
    */
  case class EngineJob(id: Int,
                       uuid: UUID,
                       name: String,
                       comment: String,
                       createdAt: JodaDateTime,
                       updatedAt: JodaDateTime,
                       jobUpdatedAt: JodaDateTime,
                       state: AnalysisJobStates.JobStates,
                       jobTypeId: String,
                       path: String,
                       jsonSettings: String,
                       createdBy: Option[String],
                       createdByEmail: Option[String],
                       smrtlinkVersion: Option[String],
                       isActive: Boolean = true,
                       errorMessage: Option[String] = None,
                       projectId: Int = JobConstants.GENERAL_PROJECT_ID,
                       isMultiJob: Boolean = false,
                       workflow: String = "{}",
                       parentMultiJobId: Option[Int] = None,
                       importedAt: Option[JodaDateTime] = None,
                       tags: String = "",
                       subJobTypeId: Option[String] = None,
                       jobStartedAt: Option[JodaDateTime] = None,
                       jobCompletedAt: Option[JodaDateTime] = None)
      extends SmrtLinkJob {

    def getJobRunTime(): Option[FiniteDuration] = {
      (jobStartedAt, jobCompletedAt) match {
        case (Some(startedAt), Some(completedAt)) =>
          val numSeconds = timeUtils.computeTimeDelta(completedAt, startedAt)
          Some(FiniteDuration(numSeconds, TimeUnit.SECONDS))
        case _ => None
      }
    }

    def toEngineCoreJob: EngineCoreJob = {
      EngineCoreJob(
        id,
        uuid,
        name,
        comment,
        createdAt,
        updatedAt,
        jobUpdatedAt,
        state,
        jobTypeId,
        path,
        jsonSettings,
        createdBy,
        createdByEmail,
        smrtlinkVersion,
        isActive,
        errorMessage,
        projectId,
        parentMultiJobId,
        tags = tags,
        subJobTypeId = subJobTypeId,
        jobStartedAt = jobStartedAt,
        jobCompletedAt = jobCompletedAt
      )
    }
  }

  // Trying to keep a clear delineation between the "Core" Engine data model and the
  // Multi-Job Engine Data Model.
  case class EngineCoreJob(id: Int,
                           uuid: UUID,
                           name: String,
                           comment: String,
                           createdAt: JodaDateTime,
                           updatedAt: JodaDateTime,
                           jobUpdatedAt: JodaDateTime,
                           state: AnalysisJobStates.JobStates,
                           jobTypeId: String,
                           path: String,
                           jsonSettings: String,
                           createdBy: Option[String],
                           createdByEmail: Option[String],
                           smrtlinkVersion: Option[String],
                           isActive: Boolean = true,
                           errorMessage: Option[String] = None,
                           projectId: Int = JobConstants.GENERAL_PROJECT_ID,
                           parentMultiJobId: Option[Int] = None,
                           tags: String = "",
                           subJobTypeId: Option[String] = None,
                           jobStartedAt: Option[JodaDateTime] = None,
                           jobCompletedAt: Option[JodaDateTime] = None)
      extends SmrtLinkJob {}

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
  case class AnalysisJobResources(path: Path,
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
  case class DataStoreFile(uniqueId: UUID,
                           sourceId: String,
                           fileTypeId: String,
                           fileSize: Long,
                           createdAt: JodaDateTime,
                           modifiedAt: JodaDateTime,
                           path: String,
                           isChunked: Boolean = false,
                           name: String,
                           description: String)
      extends ImportAble {

    def fileExists: Boolean = Paths.get(path).toFile.exists

    def summary: String = toString

    /**
      * Convert file path to be absolute starting from a base directory
      */
    def absolutize(base: Path) = copy(path = base.resolve(path).toString)

    /**
      * Convert file path to be relative to a base directory path
      */
    def relativize(base: Path) = copy(
      path = base.relativize(Paths.get(path)).toString
    )
  }

  object DataStoreFile {
    def fromMaster(path: Path): DataStoreFile = {
      val now = JodaDateTime.now()
      DataStoreFile(
        UUID.randomUUID(),
        JobConstants.DATASTORE_FILE_MASTER_LOG_ID,
        FileTypes.LOG.fileTypeId,
        0L,
        now,
        now,
        path.toString,
        isChunked = false,
        "Job Master Log",
        "Job Stdout/Log"
      )
    }

    // Create a DataStore file from file that exists.
    def fromFile(sourceId: String,
                 fileTypeId: String,
                 path: Path,
                 name: String,
                 description: Option[String] = None): DataStoreFile = {
      val now = JodaDateTime.now()

      DataStoreFile(UUID.randomUUID(),
                    sourceId,
                    fileTypeId,
                    path.toFile.length(),
                    now,
                    now,
                    path.toString,
                    false,
                    name,
                    description.getOrElse(name))

    }
  }

  // Container for file created from a Job.
  // MK. What is the purpose of this container?
  case class DataStoreJobFile(jobId: UUID, dataStoreFile: DataStoreFile)

  case class PacBioDataStore(createdAt: JodaDateTime,
                             updatedAt: JodaDateTime,
                             version: String,
                             files: Seq[DataStoreFile])
      extends ImportAble {
    override def summary = {
      s"PacBioDataStore Summary ${files.length} files Created at $createdAt Schema version $version\n" +
        files.zipWithIndex
          .map { case (d, i) => s"${i + 1}. ${d.toString}" }
          .reduceLeftOption(_ + "\n" + _)
          .getOrElse("No datastore files")
    }

    override def toString: String = summary

    /**
      * Convert all file paths to be absolute starting from a base directory
      */
    def absolutize(base: Path) = copy(files = files.map(_.absolutize(base)))

    /**
      * Convert all file paths to be relative to a base directory path
      */
    def relativize(base: Path) = copy(files = files.map(_.relativize(base)))
  }

  object PacBioDataStore {

    val SCHEMA_VERSION = "0.2.0"

    def fromFiles(files: Seq[DataStoreFile]): PacBioDataStore = {
      val now = JodaDateTime.now()
      PacBioDataStore(now, now, SCHEMA_VERSION, files)
    }
  }

  // Should think about making this a Path
  case class BoundEntryPoint(entryId: String, path: Path)

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
      else
        throw new UnsupportedOperationException(
          s"Value $v is not an allowed choice")
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

  case class PipelineStrOption(id: String,
                               name: String,
                               value: String,
                               description: String)
      extends PipelineStrOptionBase {
    val optionTypeId = OptionTypes.STR.optionTypeId
    def asServiceOption = ServiceTaskStrOption(id, value, optionTypeId)
  }

  case class PipelineIntOption(id: String,
                               name: String,
                               value: Int,
                               description: String)
      extends PipelineIntOptionBase {
    val optionTypeId = OptionTypes.INT.optionTypeId
    def asServiceOption = ServiceTaskIntOption(id, value, optionTypeId)
  }

  case class PipelineDoubleOption(id: String,
                                  name: String,
                                  value: Double,
                                  description: String)
      extends PipelineDoubleOptionBase {
    val optionTypeId = OptionTypes.FLOAT.optionTypeId
    def asServiceOption = ServiceTaskDoubleOption(id, value, optionTypeId)
  }

  case class PipelineBooleanOption(id: String,
                                   name: String,
                                   value: Boolean,
                                   description: String)
      extends PipelineBooleanOptionBase {
    val optionTypeId = OptionTypes.BOOL.optionTypeId
    def asServiceOption = ServiceTaskBooleanOption(id, value, optionTypeId)
  }

  case class PipelineChoiceStrOption(id: String,
                                     name: String,
                                     value: String,
                                     description: String,
                                     choices: Seq[String])
      extends PipelineStrOptionBase
      with PipelineChoiceOption {
    val optionTypeId = OptionTypes.CHOICE.optionTypeId
    val _ = validate(value)

    def applyValue(v: String): PipelineChoiceStrOption =
      copy(value = validate(v))
    def asServiceOption = ServiceTaskStrOption(id, value, optionTypeId)
  }

  case class PipelineChoiceIntOption(id: String,
                                     name: String,
                                     value: Int,
                                     description: String,
                                     choices: Seq[Int])
      extends PipelineIntOptionBase
      with PipelineChoiceOption {
    val optionTypeId = OptionTypes.CHOICE_INT.optionTypeId
    val _ = validate(value)

    def applyValue(v: Int): PipelineChoiceIntOption = copy(value = validate(v))
    def asServiceOption = ServiceTaskIntOption(id, value, optionTypeId)
  }

  case class PipelineChoiceDoubleOption(id: String,
                                        name: String,
                                        value: Double,
                                        description: String,
                                        choices: Seq[Double])
      extends PipelineDoubleOptionBase
      with PipelineChoiceOption {
    val optionTypeId = OptionTypes.CHOICE_FLOAT.optionTypeId
    val _ = validate(value)

    def applyValue(v: Double): PipelineChoiceDoubleOption =
      copy(value = validate(v))
    def asServiceOption = ServiceTaskDoubleOption(id, value, optionTypeId)
  }

  trait ServiceTaskOptionBase extends PacBioBaseOption {}

  trait ServiceTaskStrOptionBase extends ServiceTaskOptionBase {
    type In = String
  }
  trait ServiceTaskIntOptionBase extends ServiceTaskOptionBase {
    type In = Int
  }
  trait ServiceTaskBooleanOptionBase extends ServiceTaskOptionBase {
    type In = Boolean
  }
  trait ServiceTaskDoubleOptionBase extends ServiceTaskOptionBase {
    type In = Double
  }

  case class ServiceTaskStrOption(id: String,
                                  value: String,
                                  optionTypeId: String =
                                    OptionTypes.STR.optionTypeId)
      extends ServiceTaskStrOptionBase
  case class ServiceTaskIntOption(id: String,
                                  value: Int,
                                  optionTypeId: String =
                                    OptionTypes.INT.optionTypeId)
      extends ServiceTaskIntOptionBase
  case class ServiceTaskBooleanOption(id: String,
                                      value: Boolean,
                                      optionTypeId: String =
                                        OptionTypes.BOOL.optionTypeId)
      extends ServiceTaskBooleanOptionBase
  case class ServiceTaskDoubleOption(id: String,
                                     value: Double,
                                     optionTypeId: String =
                                       OptionTypes.FLOAT.optionTypeId)
      extends ServiceTaskDoubleOptionBase

  // Raw (aka) Direct Options. Minimal options used to call pbsmrtpipe
  case class PbsmrtpipeDirectJobOptions(
      projectId: Int = 1,
      pipelineId: String,
      entryPoints: Seq[BoundEntryPoint],
      taskOptions: Seq[ServiceTaskOptionBase],
      workflowOptions: Seq[ServiceTaskOptionBase])

  // pbsmrtpipe/smrtflow Pipelines
  case class PipelineTemplate(id: String,
                              name: String,
                              description: String,
                              version: String,
                              options: Seq[PipelineBaseOption],
                              taskOptions: Seq[PipelineBaseOption],
                              entryPoints: Seq[EntryPoint],
                              tags: Seq[String],
                              presets: Option[Seq[PipelineTemplatePreset]] =
                                None) {
    def getPresets = presets.getOrElse(Seq.empty[PipelineTemplatePreset])
  }

  // templateId refers to the PipelineTemplate Id
  case class PipelineTemplatePreset(presetId: String,
                                    pipelineId: String,
                                    options: Seq[ServiceTaskOptionBase],
                                    taskOptions: Seq[ServiceTaskOptionBase])

  // FIXME(mkocher)(2016-8-18) All of these View rules should probable be migrated to a central location
  case class DataStoreFileViewRule(sourceId: String,
                                   fileTypeId: String,
                                   isHidden: Boolean,
                                   name: Option[String],
                                   description: Option[String])
  case class PipelineDataStoreViewRules(pipelineId: String,
                                        rules: Seq[DataStoreFileViewRule],
                                        smrtlinkVersion: String)

  case class MigrationStatusRow(timestamp: String,
                                success: Boolean,
                                error: Option[String] = None)

  case class ExportJobManifest(
      job: EngineJob,
      entryPoints: Seq[BoundEntryPoint],
      datastore: Option[PacBioDataStore],
      events: Option[Seq[JobEvent]]) // This really should be required

  // Tech Support Related Models. These really belong on "common"

  object BundleTypes {
    // Given that these bundle type ids are translated to eventTypeIds,
    // these need to be prefixed with "sl" to be consistent with
    // the SL System Event eventTypeId convention (for ElasticSearch motivations)
    val FAILED_INSTALL = "sl_ts_bundle_failed_install"
    val SYSTEM_STATUS = "sl_ts_bundle_system_status"
    // Historical Job metrics bundle
    val JOB_HIST = "sl_ts_bundle_job_metrics_history"
    // Failed Job Bundle
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
                                    comment: Option[String])
      extends TsManifest

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
                           jobId: Int)
      extends TsManifest

  /**
    *
    * @param jobsJson Relative Path to the EngineJobMetrics within the
    *                 TS Bundle
    */
  case class TsJobMetricHistory(id: UUID,
                                bundleTypeId: String,
                                bundleTypeVersion: Int,
                                createdAt: JodaDateTime,
                                smrtLinkSystemId: UUID,
                                dnsName: Option[String],
                                smrtLinkSystemVersion: Option[String],
                                user: String,
                                comment: Option[String],
                                jobsJson: Path)
      extends TsManifest

}
