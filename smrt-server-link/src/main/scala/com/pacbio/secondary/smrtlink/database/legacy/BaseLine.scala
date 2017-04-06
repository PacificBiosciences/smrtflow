package com.pacbio.secondary.smrtlink.database.legacy

import java.util.UUID
import java.nio.file.{Path, Paths}

import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.PostgresDriver.api._

// This is a potential problem
import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
// This is a problem
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}

/**
  * Baseline Postgres Data Models and TableModels.
  *
  * ONLY the Sqlite to Postgres importer/migrator and
  * V1 db/migration for flyway.
  *
  */
object BaseLine extends PacBioDateTimeDatabaseFormat {

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

  case class JobEvent(
                         eventId: UUID,
                         jobId: Int,
                         state: AnalysisJobStates.JobStates,
                         message: String,
                         createdAt: JodaDateTime,
                         eventTypeId: String = JobConstants.EVENT_TYPE_JOB_STATUS)

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
                          isActive: Boolean = true,
                          errorMessage: Option[String] = None,
                          projectId: Int = JobConstants.GENERAL_PROJECT_ID) {

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
                 isActive: Boolean = true,
                 errorMessage: Option[String] = None,
                 projectId: Int = 1) = {

      // This might not be the best idea.
      val state = AnalysisJobStates.intToState(stateId) getOrElse AnalysisJobStates.UNKNOWN

      EngineJob(id, uuid, name, comment, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy, smrtlinkVersion, isActive, errorMessage, projectId)
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

  trait ImportAble

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

  case class MigrationStatusRow(timestamp: String, success: Boolean, error: Option[String] = None)

  case class RunCreate(dataModel: String)

  case class RunUpdate(dataModel: Option[String] = None, reserved: Option[Boolean] = None)

  case class RunSummary(
                           uniqueId: UUID,
                           name: String,
                           summary: Option[String],
                           createdBy: Option[String],
                           createdAt: Option[JodaDateTime],
                           startedAt: Option[JodaDateTime],
                           transfersCompletedAt: Option[JodaDateTime],
                           completedAt: Option[JodaDateTime],
                           status: SupportedRunStates,
                           totalCells: Int,
                           numCellsCompleted: Int,
                           numCellsFailed: Int,
                           instrumentName: Option[String],
                           instrumentSerialNumber: Option[String],
                           instrumentSwVersion: Option[String],
                           primaryAnalysisSwVersion: Option[String],
                           context: Option[String],
                           terminationInfo: Option[String],
                           reserved: Boolean) {

    def withDataModel(dataModel: String) = Run(
      dataModel,
      uniqueId,
      name,
      summary,
      createdBy,
      createdAt,
      startedAt,
      transfersCompletedAt,
      completedAt,
      status,
      totalCells,
      numCellsCompleted,
      numCellsFailed,
      instrumentName,
      instrumentSerialNumber,
      instrumentSwVersion,
      primaryAnalysisSwVersion,
      context,
      terminationInfo,
      reserved)
  }

  case class Run(
                    dataModel: String,
                    uniqueId: UUID,
                    name: String,
                    summary: Option[String],
                    createdBy: Option[String],
                    createdAt: Option[JodaDateTime],
                    startedAt: Option[JodaDateTime],
                    transfersCompletedAt: Option[JodaDateTime],
                    completedAt: Option[JodaDateTime],
                    status: SupportedRunStates,
                    totalCells: Int,
                    numCellsCompleted: Int,
                    numCellsFailed: Int,
                    instrumentName: Option[String],
                    instrumentSerialNumber: Option[String],
                    instrumentSwVersion: Option[String],
                    primaryAnalysisSwVersion: Option[String],
                    context: Option[String],
                    terminationInfo: Option[String],
                    reserved: Boolean) {

    def summarize = RunSummary(
      uniqueId,
      name,
      summary,
      createdBy,
      createdAt,
      startedAt,
      transfersCompletedAt,
      completedAt,
      status,
      totalCells,
      numCellsCompleted,
      numCellsFailed,
      instrumentName,
      instrumentSerialNumber,
      instrumentSwVersion,
      primaryAnalysisSwVersion,
      context,
      terminationInfo,
      reserved)
  }

  case class CollectionMetadata(
                                   runId: UUID,
                                   uniqueId: UUID,
                                   well: String,
                                   name: String,
                                   summary: Option[String],
                                   context: Option[String],
                                   collectionPathUri: Option[Path],
                                   status: SupportedAcquisitionStates,
                                   instrumentId: Option[String],
                                   instrumentName: Option[String],
                                   movieMinutes: Double,
                                   createdBy: Option[String],
                                   startedAt: Option[JodaDateTime],
                                   completedAt: Option[JodaDateTime],
                                   terminationInfo: Option[String])

  // Samples

  case class Sample(details: String, uniqueId: UUID, name: String, createdBy: String, createdAt: JodaDateTime)

  case class SampleCreate(details: String, uniqueId: UUID, name: String)

  case class SampleUpdate(details: Option[String], name: Option[String])

  case class SampleTestExamples(count: Int)

  // Registry

  case class RegistryResource(createdAt: JodaDateTime, uuid: UUID, host: String, port: Int, resourceId: String, updatedAt: JodaDateTime)

  case class RegistryResourceCreate(host: String, port: Int, resourceId: String)

  case class RegistryResourceUpdate(host: Option[String], port: Option[Int])

  case class RegistryProxyRequest(path: String, method: String, data: Option[Array[Byte]], headers: Option[Map[String, String]], params: Option[Map[String, String]])


  // Jobs

  // This is terse Status message used in sub-component endpoints root/my-endpoints/status
  case class SimpleStatus(id: String, msg: String, uptime: Long)

  case class JobTypeEndPoint(jobTypeId: String, description: String) {
    def globalId = s"jobtypes-$jobTypeId"
  }

  // Entry point use to create jobs from the Service layer. This will then be translated to a
  // BoundEntryPoint with the resolved path of the DataSet
  case class BoundServiceEntryPoint(entryId: String, fileTypeId: String, datasetId: Either[Int, UUID])

  // Entry points that are have dataset types
  case class EngineJobEntryPoint(jobId: Int, datasetUUID: UUID, datasetType: String)

  case class EngineJobEntryPointRecord(datasetUUID: UUID, datasetType: String)

  // Service related Job Tasks

  /**
    * Service Request Format to create a TaskJob
    *
    * @param uuid       Globally Unique Task UUID
    * @param taskId     task id which is lo
    * @param taskTypeId task type (i.e., tool contract type id)
    * @param name       Display name of the task
    * @param createdAt  Time when the task was created
    */
  case class CreateJobTaskRecord(uuid: UUID,
                                 taskId: String,
                                 taskTypeId: String,
                                 name: String,
                                 createdAt: JodaDateTime)

  case class UpdateJobTaskRecord(uuid: UUID, state: String, message: String, errorMessage: Option[String])

  // Need to find a better way to do this
  case class PacBioSchema(id: String, content: String)

  // New DataSet Service Models
  trait UniqueIdAble {
    val id: Int
    val uuid: UUID
  }

  trait ProjectAble {
    val createdBy: Option[String]
    val jobId: Int
    val projectId: Int
    val isActive: Boolean
  }

  case class DataSetMetaDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime, updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, tags: String, version: String, comments: String, md5: String, createdBy: Option[String], jobId: Int, projectId: Int, isActive: Boolean) extends UniqueIdAble with ProjectAble

  case class SubreadServiceSet(id: Int, uuid: UUID, cellId: String, metadataContextId: String, wellSampleName: String, wellName: String, bioSampleName: String, cellIndex: Int, instrumentId: String, instrumentName: String, runName: String, instrumentControlVersion: String) extends UniqueIdAble

  case class SubreadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: SubreadServiceSet)

  case class HdfSubreadServiceSet(
                                     id: Int,
                                     uuid: UUID,
                                     cellId: String,
                                     metadataContextId: String,
                                     wellSampleName: String,
                                     wellName: String,
                                     bioSampleName: String,
                                     cellIndex: Int,
                                     instrumentId: String,
                                     instrumentName: String,
                                     runName: String,
                                     instrumentControlVersion: String) extends UniqueIdAble

  case class HdfSubreadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: HdfSubreadServiceSet)

  case class ReferenceServiceSet(id: Int, uuid: UUID, ploidy: String, organism: String) extends UniqueIdAble

  case class ReferenceServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ReferenceServiceSet)

  case class AlignmentServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

  case class AlignmentServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: AlignmentServiceSet)

  case class BarcodeServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

  case class BarcodeServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: BarcodeServiceSet)

  case class ConsensusReadServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

  case class ConsensusReadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ConsensusReadServiceSet)

  case class GmapReferenceServiceSet(id: Int, uuid: UUID, ploidy: String, organism: String) extends UniqueIdAble

  case class GmapReferenceServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: GmapReferenceServiceSet)

  case class ConsensusAlignmentServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

  case class ConsensusAlignmentServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ConsensusAlignmentServiceSet)

  case class ContigServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

  case class ContigServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ContigServiceSet)

  // This is essentially just a flattening of the DataStoreJobFile + metadata specific to the
  // /datastore-files endpoint
  case class DataStoreServiceFile(
                                     uuid: UUID,
                                     fileTypeId: String,
                                     sourceId: String,
                                     fileSize: Long,
                                     createdAt: JodaDateTime,
                                     modifiedAt: JodaDateTime,
                                     importedAt: JodaDateTime,
                                     path: String,
                                     jobId: Int,
                                     jobUUID: UUID,
                                     name: String,
                                     description: String,
                                     isActive: Boolean = true) {

    def fileExists: Boolean = Paths.get(path).toFile.exists
  }

  // Files that have Reports
  case class DataStoreReportFile(
                                    dataStoreFile: DataStoreServiceFile,
                                    reportTypeId: String)

  /**
    * Service DataSet metadata
    *
    * See http://pacbiofileformats.readthedocs.io for more details
    *
    * @param id          dataset unique id (e.g., Pacbio.DataSet.SubreadSet)
    * @param name        Display name of dataset metadata
    * @param description Description of dataset metadata
    * @param createdAt   inserted into the database
    * @param updatedAt   last updated date in the database
    * @param shortName   short identifier (e.g., "subreads")
    */
  case class ServiceDataSetMetaType(
                                       id: String,
                                       name: String,
                                       description: String,
                                       createdAt: JodaDateTime,
                                       updatedAt: JodaDateTime,
                                       shortName: String)

  trait ServiceDataSetMetadata {
    val id: Int
    val name: String
    val uuid: UUID
    val path: String
    val createdAt: JodaDateTime
    val updatedAt: JodaDateTime
    val numRecords: Long
    val totalLength: Long
    val version: String
    val comments: String
    // Keeping this a string for now
    val tags: String
    val md5: String
    val createdBy: Option[String]
    val jobId: Int
    val projectId: Int
  }

  case class SubreadServiceDataSet(
                                      id: Int,
                                      uuid: UUID,
                                      name: String,
                                      path: String,
                                      createdAt: JodaDateTime,
                                      updatedAt: JodaDateTime,
                                      numRecords: Long,
                                      totalLength: Long,
                                      version: String,
                                      comments: String,
                                      tags: String,
                                      md5: String,
                                      instrumentName: String,
                                      metadataContextId: String,
                                      wellSampleName: String,
                                      wellName: String,
                                      bioSampleName: String,
                                      cellIndex: Int,
                                      runName: String,
                                      createdBy: Option[String],
                                      jobId: Int,
                                      projectId: Int,
                                      datasetType: String = "PacBio.DataSet.SubreadSet")
      extends ServiceDataSetMetadata

  case class HdfSubreadServiceDataSet(
                                         id: Int,
                                         uuid: UUID,
                                         name: String,
                                         path: String,
                                         createdAt: JodaDateTime,
                                         updatedAt: JodaDateTime,
                                         numRecords: Long,
                                         totalLength: Long,
                                         version: String,
                                         comments: String,
                                         tags: String,
                                         md5: String,
                                         instrumentName: String,
                                         metadataContextId: String,
                                         wellSampleName: String,
                                         wellName: String,
                                         bioSampleName: String,
                                         cellIndex: Int,
                                         runName: String,
                                         createdBy: Option[String],
                                         jobId: Int,
                                         projectId: Int,
                                         datasetType: String = "PacBio.DataSet.HdfSubreadSet")
      extends ServiceDataSetMetadata

  case class ReferenceServiceDataSet(
                                        id: Int,
                                        uuid: UUID,
                                        name: String,
                                        path: String,
                                        createdAt: JodaDateTime,
                                        updatedAt: JodaDateTime,
                                        numRecords: Long,
                                        totalLength: Long,
                                        version: String,
                                        comments: String,
                                        tags: String,
                                        md5: String,
                                        createdBy: Option[String],
                                        jobId: Int,
                                        projectId: Int,
                                        ploidy: String,
                                        organism: String,
                                        datasetType: String = "PacBio.DataSet.ReferenceSet")
      extends ServiceDataSetMetadata

  case class AlignmentServiceDataSet(
                                        id: Int,
                                        uuid: UUID,
                                        name: String,
                                        path: String,
                                        createdAt: JodaDateTime,
                                        updatedAt: JodaDateTime,
                                        numRecords: Long,
                                        totalLength: Long,
                                        version: String,
                                        comments: String,
                                        tags: String,
                                        md5: String,
                                        createdBy: Option[String],
                                        jobId: Int,
                                        projectId: Int,
                                        datasetType: String = "PacBio.DataSet.AlignmentSet")
      extends ServiceDataSetMetadata

  case class ConsensusReadServiceDataSet(
                                            id: Int,
                                            uuid: UUID,
                                            name: String,
                                            path: String,
                                            createdAt: JodaDateTime,
                                            updatedAt: JodaDateTime,
                                            numRecords: Long,
                                            totalLength: Long,
                                            version: String,
                                            comments: String,
                                            tags: String,
                                            md5: String,
                                            createdBy: Option[String],
                                            jobId: Int,
                                            projectId: Int,
                                            datasetType: String = "PacBio.DataSet.ConsensusReadSet")
      extends ServiceDataSetMetadata

  case class ConsensusAlignmentServiceDataSet(
                                                 id: Int,
                                                 uuid: UUID,
                                                 name: String,
                                                 path: String,
                                                 createdAt: JodaDateTime,
                                                 updatedAt: JodaDateTime,
                                                 numRecords: Long,
                                                 totalLength: Long,
                                                 version: String,
                                                 comments: String,
                                                 tags: String,
                                                 md5: String,
                                                 createdBy: Option[String],
                                                 jobId: Int,
                                                 projectId: Int,
                                                 datasetType: String = "PacBio.DataSet.ConsensusAlignmentSet")
      extends ServiceDataSetMetadata

  case class BarcodeServiceDataSet(
                                      id: Int,
                                      uuid: UUID,
                                      name: String,
                                      path: String,
                                      createdAt: JodaDateTime,
                                      updatedAt: JodaDateTime,
                                      numRecords: Long,
                                      totalLength: Long,
                                      version: String,
                                      comments: String,
                                      tags: String,
                                      md5: String,
                                      createdBy: Option[String],
                                      jobId: Int,
                                      projectId: Int,
                                      datasetType: String = "PacBio.DataSet.BarcodeSet")
      extends ServiceDataSetMetadata

  case class ContigServiceDataSet(
                                     id: Int,
                                     uuid: UUID,
                                     name: String,
                                     path: String,
                                     createdAt: JodaDateTime,
                                     updatedAt: JodaDateTime,
                                     numRecords: Long,
                                     totalLength: Long,
                                     version: String,
                                     comments: String,
                                     tags: String,
                                     md5: String,
                                     createdBy: Option[String],
                                     jobId: Int,
                                     projectId: Int,
                                     datasetType: String = "PacBio.DataSet.ContigSet")
      extends ServiceDataSetMetadata

  case class GmapReferenceServiceDataSet(
                                            id: Int,
                                            uuid: UUID,
                                            name: String,
                                            path: String,
                                            createdAt: JodaDateTime,
                                            updatedAt: JodaDateTime,
                                            numRecords: Long,
                                            totalLength: Long,
                                            version: String,
                                            comments: String,
                                            tags: String,
                                            md5: String,
                                            createdBy: Option[String],
                                            jobId: Int,
                                            projectId: Int,
                                            ploidy: String,
                                            organism: String,
                                            datasetType: String = "PacBio.DataSet.GmapReferenceSet")
      extends ServiceDataSetMetadata

  object ProjectState {

    sealed trait ProjectState

    case object CREATED extends ProjectState

    case object ACTIVE extends ProjectState

    // LEGACY STATES
    // TODO(smcclellan): Clean/delete rows with UPDATED state?
    case object UPDATED extends ProjectState

    def fromString(s: String): ProjectState = {
      Seq(CREATED, ACTIVE, UPDATED)
          .find(_.toString == s)
          .getOrElse(throw new IllegalArgumentException(s"Unknown project state $s, acceptable values are $CREATED, $ACTIVE"))
    }
  }

  object ProjectPermissions {
    import ProjectUserRole._

    sealed abstract class ProjectPermissions(val grantToAll: Set[ProjectUserRole])
    case object ALL_CAN_EDIT extends ProjectPermissions(grantToAll = Set(CAN_EDIT, CAN_VIEW))
    case object ALL_CAN_VIEW extends ProjectPermissions(grantToAll = Set(CAN_VIEW))
    case object USER_SPECIFIC extends ProjectPermissions(grantToAll = Set.empty)

    def fromString(s: String): ProjectPermissions =
      Seq(ALL_CAN_EDIT, ALL_CAN_VIEW, USER_SPECIFIC)
        .find(_.toString == s)
        .getOrElse(throw new IllegalArgumentException(s"Unknown project permissions $s, acceptable values are $ALL_CAN_EDIT, $ALL_CAN_VIEW, $USER_SPECIFIC"))
  }

  // We have a simpler (cheaper to query) project case class for the API
  // response that lists many projects,
  case class Project(
                        id: Int,
                        name: String,
                        description: String,
                        state: ProjectState.ProjectState,
                        createdAt: JodaDateTime,
                        updatedAt: JodaDateTime,
                        // isActive: false if the project has been deleted, true otherwise
                        isActive: Boolean,
                        permissions: ProjectPermissions.ProjectPermissions = ProjectPermissions.USER_SPECIFIC) {

    def makeFull(datasets: Seq[DataSetMetaDataSet], members: Seq[ProjectRequestUser]): FullProject =
      FullProject(
        id,
        name,
        description,
        state,
        createdAt,
        updatedAt,
        isActive,
        permissions,
        datasets,
        members)
  }

  // and a more detailed case class for the API responses involving
  // individual projects.
  case class FullProject(
                            id: Int,
                            name: String,
                            description: String,
                            state: ProjectState.ProjectState,
                            createdAt: JodaDateTime,
                            updatedAt: JodaDateTime,
                            isActive: Boolean,
                            permissions: ProjectPermissions.ProjectPermissions,
                            datasets: Seq[DataSetMetaDataSet],
                            members: Seq[ProjectRequestUser]) {

    def asRequest: ProjectRequest =
      ProjectRequest(
        name,
        description,
        Some(state),
        Some(datasets.map(ds => RequestId(ds.id))),
        Some(members.map(u => ProjectRequestUser(u.login, u.role))))
  }

  // the json structures required in client requests are a subset of the
  // FullProject structure (the FullProject is a valid request, but many
  // fields are optional in requests).
  case class ProjectRequest(
                               name: String,
                               description: String,
                               // if any of these are None in a PUT request, the corresponding
                               // value will stay the same (i.e., the update will be skipped).
                               state: Option[ProjectState.ProjectState],
                               datasets: Option[Seq[RequestId]],
                               members: Option[Seq[ProjectRequestUser]]) {

    // this returns a copy!
    def appendDataSet(dsId: Int): ProjectRequest = {
      val allDatasets = datasets.map(ds => ds ++ Seq(RequestId(dsId))).getOrElse(Seq(RequestId(dsId)))
      this.copy(datasets = Some(allDatasets))
    }
  }

  case class RequestId(id: Int)

  object ProjectUserRole {

    sealed trait ProjectUserRole

    case object OWNER extends ProjectUserRole

    case object CAN_EDIT extends ProjectUserRole

    case object CAN_VIEW extends ProjectUserRole

    def fromString(r: String): ProjectUserRole = {
      Seq(OWNER, CAN_EDIT, CAN_VIEW)
          .find(_.toString == r)
          .getOrElse(throw new IllegalArgumentException(s"Unknown project user role $r, acceptable values are $OWNER, $CAN_EDIT, $CAN_VIEW"))
    }
  }

  case class ProjectRequestUser(login: String, role: ProjectUserRole.ProjectUserRole)

  case class ProjectUser(projectId: Int, login: String, role: ProjectUserRole.ProjectUserRole)

  case class EulaRecord(user: String, acceptedAt: JodaDateTime, smrtlinkVersion: String, osVersion: String, enableInstallMetrics: Boolean, enableJobMetrics: Boolean)

  // Table Models

  implicit val jobStateType = MappedColumnType.base[AnalysisJobStates.JobStates, String](
    { s => s.toString }, { s => AnalysisJobStates.toState(s).getOrElse(AnalysisJobStates.UNKNOWN) }
  )

  class JobEventsT(tag: Tag) extends Table[JobEvent](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def eventTypeId: Rep[String] = column[String]("event_type_id")

    def state: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def * = (id, jobId, state, message, createdAt, eventTypeId) <> (JobEvent.tupled, JobEvent.unapply)

    def idx = index("job_events_job_id", jobId)
  }

  class JobTasks(tag: Tag) extends Table[JobTask](tag, "job_tasks") {

    def uuid: Rep[UUID] = column[UUID]("task_uuid", O.PrimaryKey)

    def idx = index("index_uuid", uuid, unique = true)

    def jobId: Rep[Int] = column[Int]("job_id")

    // Both the taskId and uuid should really be in the Resolved Tool Contract
    // This is only unique to the Job
    def taskId: Rep[String] = column[String]("task_id")

    // This is the ToolContract Id
    def taskTypeId: Rep[String] = column[String]("task_type_id")

    // Ideally, this should use the states defined here AnalysisJobStates.JobStates for tasks
    def state: Rep[String] = column[String]("task_state")

    // Display Name of the Task
    def name: Rep[String] = column[String]("task_name")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    // Should there be a runTime ? Because a task created, doesn't necessarily
    // mean the task has started to run. It can be waiting in the Scheduler (e.g., SGE) queue

    // Optional Error Message
    def errorMessage: Rep[Option[String]] = column[Option[String]]("error_message")

    def * = (uuid, jobId, taskId, taskTypeId, name, state, createdAt, updatedAt, errorMessage) <> (JobTask.tupled, JobTask.unapply)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)


  }

  class EngineJobsT(tag: Tag) extends Table[EngineJob](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")

    // This should have been description, but keeping with the Underlying EngineJob data model and
    // avoiding serialization backward compatibility issues
    def comment: Rep[String] = column[String]("comment")

    def name: Rep[String] = column[String]("name")

    def state: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying = true))

    // This should be stored as JSON within slick-pg
    // https://github.com/tminglei/slick-pg
    def jsonSettings: Rep[String] = column[String]("json_settings")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def smrtLinkVersion: Rep[Option[String]] = column[Option[String]]("smrtlink_version")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    // Optional Error Message
    def errorMessage: Rep[Option[String]] = column[Option[String]]("error_message")

    def projectId: Rep[Int] = column[Int]("project_id")

    def findByUUID(uuid: UUID) = engineJobs.filter(_.uuid === uuid)

    def findById(i: Int) = engineJobs.filter(_.id === i)

    def * = (id, uuid, name, comment, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy, smrtLinkVersion, isActive, errorMessage, projectId) <> (EngineJob.tupled, EngineJob.unapply)

    def uuidIdx = index("engine_jobs_uuid", uuid)

    def typeIdx = index("engine_jobs_job_type", jobTypeId)

    def projectIdFK = foreignKey("project_id_fk", projectId, projects)(_.id)
  }

  implicit val projectStateType = MappedColumnType.base[ProjectState.ProjectState, String](
    { s => s.toString }, { s => ProjectState.fromString(s) }
  )
  implicit val projectPermissionsType = MappedColumnType.base[ProjectPermissions.ProjectPermissions, String](
    { s => s.toString }, { s => ProjectPermissions.fromString(s) }
  )

  class ProjectsT(tag: Tag) extends Table[Project](tag, "projects") {

    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    //Marketing request that the Project name must be unique
    def nameIdx = index("project_name", name, unique = true)

    def description: Rep[String] = column[String]("description")

    def state: Rep[ProjectState.ProjectState] = column[ProjectState.ProjectState]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def permissions: Rep[ProjectPermissions.ProjectPermissions] = column[ProjectPermissions.ProjectPermissions]("permissions")

    def * = (id, name, description, state, createdAt, updatedAt, isActive, permissions) <> (Project.tupled, Project.unapply)
  }

  implicit val projectUserRoleType = MappedColumnType.base[ProjectUserRole.ProjectUserRole, String](
    { r => r.toString }, { r => ProjectUserRole.fromString(r) }
  )

  class ProjectsUsersT(tag: Tag) extends Table[ProjectUser](tag, "projects_users") {
    def projectId: Rep[Int] = column[Int]("project_id")

    def login: Rep[String] = column[String]("login")

    def role: Rep[ProjectUserRole.ProjectUserRole] = column[ProjectUserRole.ProjectUserRole]("role")

    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)

    def * = (projectId, login, role) <> (ProjectUser.tupled, ProjectUser.unapply)

    def loginIdx = index("projects_users_login", login)

    def projectIdIdx = index("projects_users_project_id", projectId)
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class DataSetTypesT(tag: Tag) extends Table[ServiceDataSetMetaType](tag, "pacbio_dataset_metatypes") {

    def id: Rep[String] = column[String]("dataset_type_id", O.PrimaryKey)

    def idx = index("index_id", id, unique = true)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def shortName: Rep[String] = column[String]("short_name")

    def * = (id, name, description, createdAt, updatedAt, shortName) <> (ServiceDataSetMetaType.tupled, ServiceDataSetMetaType.unapply)
  }

  /*
  Table to capture the DataSet Entry points of a EngineJob

  - Job has many datasets
  - DataSet may belong to one or many jobs
   */
  class EngineJobDataSetT(tag: Tag) extends Table[EngineJobEntryPoint](tag, "engine_jobs_datasets") {

    def jobId: Rep[Int] = column[Int]("job_id")

    def datasetUUID: Rep[UUID] = column[UUID]("dataset_uuid")

    def datasetType: Rep[String] = column[String]("dataset_type")

    def * = (jobId, datasetUUID, datasetType) <> (EngineJobEntryPoint.tupled, EngineJobEntryPoint.unapply)

    def idx = index("engine_jobs_datasets_job_id", jobId)
  }

  /**
    * Base Metadata for all PacBio DataSets
    *
    * For each Subclass of this, consult the XSD for details on the core fields of the DataSet type.
    *
    * @param tag
    */
  class DataSetMetaT(tag: Tag) extends IdAbleTable[DataSetMetaDataSet](tag, "dataset_metadata") {

    def name: Rep[String] = column[String]("name")

    def path: Rep[String] = column[String]("path", O.Length(500, varying = true))

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def numRecords: Rep[Long] = column[Long]("num_records")

    def totalLength: Rep[Long] = column[Long]("total_length")

    // Clarify this. This should be a comma separated value? Should this be removed until actually needed?
    // Should be done with an array/list natively in postgres?
    def tags: Rep[String] = column[String]("tags")

    // Version of the dataset schema spec? This isn't completely clear. Consult the PacBioFileFormat spec for clarification.
    def version: Rep[String] = column[String]("version")

    def comments: Rep[String] = column[String]("comments")

    def md5: Rep[String] = column[String]("md5")

    def createdBy: Rep[Option[String]] = column[Option[String]]("user_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def projectId: Rep[Int] = column[Int]("project_id")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * = (id, uuid, name, path, createdAt, updatedAt, numRecords, totalLength, tags, version, comments, md5, createdBy, jobId, projectId, isActive) <> (DataSetMetaDataSet.tupled, DataSetMetaDataSet.unapply)

    def uuidIdx = index("dataset_metadata_uuid", uuid)

    def projectIdIdx = index("dataset_metadata_project_id", projectId)
  }

  class SubreadDataSetT(tag: Tag) extends IdAbleTable[SubreadServiceSet](tag, "dataset_subreads") {

    def cellId: Rep[String] = column[String]("cell_id")

    def metadataContextId: Rep[String] = column[String]("metadata_context_id")

    def wellSampleName: Rep[String] = column[String]("well_sample_name")

    def wellName: Rep[String] = column[String]("well_name")

    def bioSampleName: Rep[String] = column[String]("bio_sample_name")

    def cellIndex: Rep[Int] = column[Int]("cell_index")

    def instrumentId: Rep[String] = column[String]("instrument_id")

    def instrumentName: Rep[String] = column[String]("instrument_name")

    def runName: Rep[String] = column[String]("run_name")

    def instrumentControlVersion: Rep[String] = column[String]("instrument_control_version")

    def * = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion) <> (SubreadServiceSet.tupled, SubreadServiceSet.unapply)
  }

  class HdfSubreadDataSetT(tag: Tag) extends IdAbleTable[HdfSubreadServiceSet](tag, "dataset_hdfsubreads") {

    def cellId: Rep[String] = column[String]("cell_id")

    def metadataContextId: Rep[String] = column[String]("metadata_context_id")

    def wellSampleName: Rep[String] = column[String]("well_sample_name")

    def wellName: Rep[String] = column[String]("well_name")

    def bioSampleName: Rep[String] = column[String]("bio_sample_name")

    def cellIndex: Rep[Int] = column[Int]("cell_index")

    def instrumentId: Rep[String] = column[String]("instrument_id")

    def instrumentName: Rep[String] = column[String]("instrument_name")

    def runName: Rep[String] = column[String]("run_name")

    def instrumentControlVersion: Rep[String] = column[String]("instrument_control_version")

    def * = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion) <> (HdfSubreadServiceSet.tupled, HdfSubreadServiceSet.unapply)
  }

  class ReferenceDataSetT(tag: Tag) extends IdAbleTable[ReferenceServiceSet](tag, "dataset_references") {

    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * = (id, uuid, ploidy, organism) <> (ReferenceServiceSet.tupled, ReferenceServiceSet.unapply)
  }

  class GmapReferenceDataSetT(tag: Tag) extends IdAbleTable[GmapReferenceServiceSet](tag, "dataset_gmapreferences") {

    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * = (id, uuid, ploidy, organism) <> (GmapReferenceServiceSet.tupled, GmapReferenceServiceSet.unapply)
  }

  class AlignmentDataSetT(tag: Tag) extends IdAbleTable[AlignmentServiceSet](tag, "datasets_alignments") {
    def * = (id, uuid) <> (AlignmentServiceSet.tupled, AlignmentServiceSet.unapply)
  }

  class BarcodeDataSetT(tag: Tag) extends IdAbleTable[BarcodeServiceSet](tag, "datasets_barcodes") {
    def * = (id, uuid) <> (BarcodeServiceSet.tupled, BarcodeServiceSet.unapply)
  }

  class CCSreadDataSetT(tag: Tag) extends IdAbleTable[ConsensusReadServiceSet](tag, "datasets_ccsreads") {
    def * = (id, uuid) <> (ConsensusReadServiceSet.tupled, ConsensusReadServiceSet.unapply)
  }

  class ConsensusAlignmentDataSetT(tag: Tag) extends IdAbleTable[ConsensusAlignmentServiceSet](tag, "datasets_ccsalignments") {
    def * = (id, uuid) <> (ConsensusAlignmentServiceSet.tupled, ConsensusAlignmentServiceSet.unapply)
  }

  class ContigDataSetT(tag: Tag) extends IdAbleTable[ContigServiceSet](tag, "datasets_contigs") {
    def * = (id, uuid) <> (ContigServiceSet.tupled, ContigServiceSet.unapply)
  }

  /**
    * The general metadata container for DataStoreFile(s) produced by an EngineJob.
    *
    * A "DataStore" is a list of DataStoreFile(s) with a sourceId that is unique within the Job.
    *
    * @param tag
    */
  class PacBioDataStoreFileT(tag: Tag) extends Table[DataStoreServiceFile](tag, "datastore_files") {
    def uuid: Rep[UUID] = column[UUID]("uuid", O.PrimaryKey)

    def fileTypeId: Rep[String] = column[String]("file_type_id")

    def sourceId: Rep[String] = column[String]("source_id")

    def fileSize: Rep[Long] = column[Long]("file_size")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def modifiedAt: Rep[JodaDateTime] = column[JodaDateTime]("modified_at")

    def importedAt: Rep[JodaDateTime] = column[JodaDateTime]("imported_at")

    def path: Rep[String] = column[String]("path", O.Length(500, varying = true))

    // job id output datastore. Perhaps need to define input for jobs that have datastore's as input
    // This needs to be rethought.
    def jobId: Rep[Int] = column[Int]("job_id")

    def jobUUID: Rep[UUID] = column[UUID]("job_uuid")

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID, name, description, isActive) <> (DataStoreServiceFile.tupled, DataStoreServiceFile.unapply)

    def uuidIdx = index("datastore_files_uuid", uuid)

    def jobIdIdx = index("datastore_files_job_id", jobId)

    def jobUuidIdx = index("datastore_files_job_uuid", jobUUID)
  }

  implicit val runStatusType = MappedColumnType.base[SupportedRunStates, String](
    { s => s.value() }, { s => SupportedRunStates.fromValue(s) }
  )

  class RunSummariesT(tag: Tag) extends Table[RunSummary](tag, "run_summaries") {

    def uniqueId: Rep[UUID] = column[UUID]("unique_id", O.PrimaryKey)

    def name: Rep[String] = column[String]("name")

    def summary: Rep[Option[String]] = column[Option[String]]("summary")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def createdAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("created_at")

    def startedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("started_at")

    def transfersCompletedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("transfers_completed_at")

    def completedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("completed_at")

    def status: Rep[SupportedRunStates] = column[SupportedRunStates]("status")

    def totalCells: Rep[Int] = column[Int]("total_cells")

    def numCellsCompleted: Rep[Int] = column[Int]("num_cells_completed")

    def numCellsFailed: Rep[Int] = column[Int]("num_cells_failed")

    def instrumentName: Rep[Option[String]] = column[Option[String]]("instrument_name")

    def instrumentSerialNumber: Rep[Option[String]] = column[Option[String]]("instrument_serial_number")

    def instrumentSwVersion: Rep[Option[String]] = column[Option[String]]("instrument_sw_version")

    def primaryAnalysisSwVersion: Rep[Option[String]] = column[Option[String]]("primary_analysis_sw_version")

    def context: Rep[Option[String]] = column[Option[String]]("context")

    def terminationInfo: Rep[Option[String]] = column[Option[String]]("termination_info")

    def reserved: Rep[Boolean] = column[Boolean]("reserved")

    def * = (
        uniqueId,
        name,
        summary,
        createdBy,
        createdAt,
        startedAt,
        transfersCompletedAt,
        completedAt,
        status,
        totalCells,
        numCellsCompleted,
        numCellsFailed,
        instrumentName,
        instrumentSerialNumber,
        instrumentSwVersion,
        primaryAnalysisSwVersion,
        context,
        terminationInfo,
        reserved) <> (RunSummary.tupled, RunSummary.unapply)
  }

  case class DataModelAndUniqueId(dataModel: String, uniqueId: UUID)

  class DataModelsT(tag: Tag) extends Table[DataModelAndUniqueId](tag, "pb_data_models") {
    def uniqueId: Rep[UUID] = column[UUID]("unique_id", O.PrimaryKey)

    def dataModel: Rep[String] = column[String]("data_model", O.SqlType("TEXT"))

    def * = (dataModel, uniqueId) <> (DataModelAndUniqueId.tupled, DataModelAndUniqueId.unapply)

    def summary = foreignKey("summary_fk", uniqueId, runSummaries)(_.uniqueId)
  }

  implicit val pathType = MappedColumnType.base[Path, String](_.toString, Paths.get(_))
  implicit val collectionStatusType =
    MappedColumnType.base[SupportedAcquisitionStates, String](_.value(), SupportedAcquisitionStates.fromValue)

  class CollectionMetadataT(tag: Tag) extends Table[CollectionMetadata](tag, "collection_metadata") {
    def runId: Rep[UUID] = column[UUID]("run_id")

    def run = foreignKey("run_fk", runId, runSummaries)(_.uniqueId)

    def uniqueId: Rep[UUID] = column[UUID]("unique_id", O.PrimaryKey)

    def well: Rep[String] = column[String]("well")

    def name: Rep[String] = column[String]("name")

    def summary: Rep[Option[String]] = column[Option[String]]("column")

    def context: Rep[Option[String]] = column[Option[String]]("context")

    def collectionPathUri: Rep[Option[Path]] = column[Option[Path]]("collection_path_uri")

    def status: Rep[SupportedAcquisitionStates] = column[SupportedAcquisitionStates]("status")

    def instrumentId: Rep[Option[String]] = column[Option[String]]("instrument_id")

    def instrumentName: Rep[Option[String]] = column[Option[String]]("instrument_name")

    def movieMinutes: Rep[Double] = column[Double]("movie_minutes")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def startedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("started_at")

    def completedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("completed_at")

    def terminationInfo: Rep[Option[String]] = column[Option[String]]("termination_info")

    def * = (
        runId,
        uniqueId,
        name,
        well,
        summary,
        context,
        collectionPathUri,
        status,
        instrumentId,
        instrumentName,
        movieMinutes,
        createdBy,
        startedAt,
        completedAt,
        terminationInfo) <> (CollectionMetadata.tupled, CollectionMetadata.unapply)

    def idx = index("collection_metadata_run_id", runId)
  }

  class SampleT(tag: Tag) extends Table[Sample](tag, "SAMPLE") {

    def details: Rep[String] = column[String]("DETAILS")

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def createdBy: Rep[String] = column[String]("CREATED_BY")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def * = (details, uniqueId, name, createdBy, createdAt) <> (Sample.tupled, Sample.unapply)
  }


  class EulaRecordT(tag: Tag) extends Table[EulaRecord](tag, "eula_record") {
    def user: Rep[String] = column[String]("user")

    def acceptedAt: Rep[JodaDateTime] = column[JodaDateTime]("accepted_at")

    def smrtlinkVersion: Rep[String] = column[String]("smrtlink_version", O.PrimaryKey)

    def enableInstallMetrics: Rep[Boolean] = column[Boolean]("enable_install_metrics")

    def enableJobMetrics: Rep[Boolean] = column[Boolean]("enable_job_metrics")

    def osVersion: Rep[String] = column[String]("os_version")

    def * = (user, acceptedAt, smrtlinkVersion, osVersion, enableInstallMetrics, enableJobMetrics) <> (EulaRecord.tupled, EulaRecord.unapply)
  }

  // This is the Legacy Migration from sqlite to postgres. Adding this here for consistency (i.e.,
  // All 4.1 installs will have this table)
  // This can be dropped when the sqlite import is not longer supported.
  class MigrationStatusT(tag: Tag) extends Table[MigrationStatusRow](tag, "migration_status") {
    def timestamp: Rep[String] = column[String]("timestamp")

    def success: Rep[Boolean] = column[Boolean]("success")

    def error: Rep[Option[String]] = column[Option[String]]("error")

    def * = (timestamp, success, error) <> (MigrationStatusRow.tupled, MigrationStatusRow.unapply)
  }


  // DataSet types
  lazy val dsMetaData2 = TableQuery[DataSetMetaT]
  lazy val dsSubread2 = TableQuery[SubreadDataSetT]
  lazy val dsHdfSubread2 = TableQuery[HdfSubreadDataSetT]
  lazy val dsReference2 = TableQuery[ReferenceDataSetT]
  lazy val dsAlignment2 = TableQuery[AlignmentDataSetT]
  lazy val dsBarcode2 = TableQuery[BarcodeDataSetT]
  lazy val dsCCSread2 = TableQuery[CCSreadDataSetT]
  lazy val dsGmapReference2 = TableQuery[GmapReferenceDataSetT]
  lazy val dsCCSAlignment2 = TableQuery[ConsensusAlignmentDataSetT]
  lazy val dsContig2 = TableQuery[ContigDataSetT]

  lazy val datastoreServiceFiles = TableQuery[PacBioDataStoreFileT]

  // Users and Projects
  lazy val projects = TableQuery[ProjectsT]
  lazy val projectsUsers = TableQuery[ProjectsUsersT]

  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val engineJobsDataSets = TableQuery[EngineJobDataSetT]
  lazy val jobEvents = TableQuery[JobEventsT]
  lazy val jobTasks = TableQuery[JobTasks]

  // DataSet types
  lazy val datasetMetaTypes = TableQuery[DataSetTypesT]

  // Runs
  lazy val runSummaries = TableQuery[RunSummariesT]
  lazy val dataModels = TableQuery[DataModelsT]
  lazy val collectionMetadata = TableQuery[CollectionMetadataT]

  // Samples
  lazy val samples = TableQuery[SampleT]

  // EULA
  lazy val eulas = TableQuery[EulaRecordT]

  // Legacy Import migration table
  lazy val migrationStatus = TableQuery[MigrationStatusT]

  final type SlickTable = TableQuery[_ <: Table[_]]

  lazy val serviceTables: Set[SlickTable] = Set(
    engineJobs,
    datasetMetaTypes,
    engineJobsDataSets,
    jobEvents,
    jobTasks,
    projectsUsers,
    projects,
    dsMetaData2,
    dsSubread2,
    dsHdfSubread2,
    dsReference2,
    dsAlignment2,
    dsBarcode2,
    dsCCSread2,
    dsGmapReference2,
    dsCCSAlignment2,
    dsContig2,
    datastoreServiceFiles,
    eulas, migrationStatus)

  lazy val runTables: Set[SlickTable] = Set(runSummaries, dataModels, collectionMetadata, samples)

  lazy val allTables: Set[SlickTable] = serviceTables ++ runTables

  lazy val schema = allTables.map(_.schema).reduce(_ ++ _)


  // Note, the project name is a unique identifier
  val generalProject = Project(1, "General Project", "General SMRT Link project. By default all imported datasets and analysis jobs will be assigned to this project", ProjectState.CREATED, JodaDateTime.now(), JodaDateTime.now(), isActive = true, permissions = ProjectPermissions.ALL_CAN_VIEW)
  // This is "admin" from the wso2 model
  val projectUser = ProjectUser(generalProject.id, "admin", ProjectUserRole.OWNER)

  // Is this even used?
  private val datasetTypeDatum = List(
    ("PacBio.DataSet.ReferenceSet", "Display name for PacBio.DataSet.ReferenceSet", "Description for PacBio.DataSet.ReferenceSet", JodaDateTime.now(), JodaDateTime.now(), "references"),
    ("PacBio.DataSet.ConsensusReadSet", "Display name for PacBio.DataSet.ConsensusReadSet", "Description for PacBio.DataSet.ConsensusReadSet", JodaDateTime.now(), JodaDateTime.now(), "ccsreads"),
    ("PacBio.DataSet.ContigSet", "Display name for PacBio.DataSet.ContigSet", "Description for PacBio.DataSet.ContigSet", JodaDateTime.now(), JodaDateTime.now(), "contigs"),
    ("PacBio.DataSet.SubreadSet", "Display name for PacBio.DataSet.SubreadSet", "Description for PacBio.DataSet.SubreadSet", JodaDateTime.now(), JodaDateTime.now(), "subreads"),
    ("PacBio.DataSet.BarcodeSet", "Display name for PacBio.DataSet.BarcodeSet", "Description for PacBio.DataSet.BarcodeSet", JodaDateTime.now(), JodaDateTime.now(), "barcodes"),
    ("PacBio.DataSet.ConsensusAlignmentSet", "Display name for PacBio.DataSet.ConsensusAlignmentSet", "Description for PacBio.DataSet.ConsensusAlignmentSet", JodaDateTime.now(), JodaDateTime.now(), "ccsalignments"),
    ("PacBio.DataSet.HdfSubreadSet", "Display name for PacBio.DataSet.HdfSubreadSet", "Description for PacBio.DataSet.HdfSubreadSet", JodaDateTime.now(), JodaDateTime.now(), "hdfsubreads"),
    ("PacBio.DataSet.AlignmentSet", "Display name for PacBio.DataSet.AlignmentSet", "Description for PacBio.DataSet.AlignmentSet", JodaDateTime.now(), JodaDateTime.now(), "alignments")
  )

  val coreDataSetMetaTypes = datasetTypeDatum.map(ServiceDataSetMetaType.tupled)

}
