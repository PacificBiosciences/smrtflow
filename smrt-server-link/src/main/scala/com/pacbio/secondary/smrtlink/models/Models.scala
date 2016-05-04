package com.pacbio.secondary.smrtlink.models

import java.util.UUID

import com.pacificbiosciences.pacbiobasedatamodel.{SupportedRunStates, SupportedAcquisitionStates}
import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.common.auth.ApiUser
import com.pacbio.common.models.UserResponse
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes._
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, JobModels}

object Models

// Runs

case class RunCreate(dataModel: String)

case class RunUpdate(dataModel: Option[String] = None, reserved: Option[Boolean] = None)

case class RunSummary(uniqueId: UUID,
                      name: String,
                      summary: Option[String],
                      createdBy: Option[String],
                      createdAt: Option[JodaDateTime],
                      startedAt: Option[JodaDateTime],
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
  def withDataModel(dataModel: String) = Run(dataModel, uniqueId, name, summary, createdBy, createdAt, startedAt,
                                             completedAt, status, totalCells, numCellsCompleted, numCellsFailed,
                                             instrumentName, instrumentSerialNumber, instrumentSwVersion,
                                             primaryAnalysisSwVersion, context, terminationInfo, reserved)
}

case class Run(dataModel: String,
               uniqueId: UUID,
               name: String,
               summary: Option[String],
               createdBy: Option[String],
               createdAt: Option[JodaDateTime],
               startedAt: Option[JodaDateTime],
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

  def summarize = RunSummary(uniqueId, name, summary, createdBy, createdAt, startedAt, completedAt, status, totalCells,
                             numCellsCompleted, numCellsFailed, instrumentName, instrumentSerialNumber,
                             instrumentSwVersion, primaryAnalysisSwVersion, context, terminationInfo, reserved)
}

case class CollectionMetadata(runId: UUID,
                              uniqueId: UUID,
                              well: String,
                              name: String,
                              summary: Option[String],
                              context: Option[String],
                              status: SupportedAcquisitionStates,
                              instrumentId: Option[String],
                              instrumentName: Option[String],
                              movieMinutes: Double,
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
case class BoundServiceEntryPoint(entryId: String, fileTypeId: String, datasetId: Int)

// Entry points that are have dataset types
case class EngineJobEntryPoint(jobId: Int, datasetUUID: UUID, datasetType: String)

case class EngineJobEntryPointRecord(datasetUUID: UUID, datasetType: String)

case class EngineJobResponse(id: Int,
                             uuid: UUID,
                             name: String,
                             comment: String,
                             createdAt: JodaDateTime,
                             updatedAt: JodaDateTime,
                             state: AnalysisJobStates.JobStates,
                             jobTypeId: String,
                             path: String,
                             jsonSettings: String,
                             createdBy: Option[UserResponse])

object EngineJobResponse {
  def fromEngineJob(job: JobModels.EngineJob, user: Option[ApiUser]) =
    EngineJobResponse(job.id, job.uuid, job.name, job.comment, job.createdAt,
                      job.updatedAt, job.state, job.jobTypeId, job.path,
                      job.jsonSettings, user.map(_.toResponse))
}


// Need to find a better way to do this
case class PacBioSchema(id: String, content: String)


// Keep everything as strings. Let pbsmrtpipe sort it out
trait ServiceTaskOptionBase {
  type In
  val id: String
  val optionTypeId: String
  val value: In
}

trait ServiceTaskStrOptionBase extends ServiceTaskOptionBase{ type In = String }
trait ServiceTaskIntOptionBase extends ServiceTaskOptionBase{ type In = Int }
trait ServiceTaskBooleanOptionBase extends ServiceTaskOptionBase{ type In = Boolean }
trait ServiceTaskDoubleOptionBase extends ServiceTaskOptionBase{ type In = Double }
trait ServiceTaskFloatOptionBase extends ServiceTaskOptionBase{ type In = Float }

case class ServiceTaskStrOption(id: String, value: String, optionTypeId: String) extends ServiceTaskStrOptionBase
case class ServiceTaskIntOption(id: String, value: Int, optionTypeId: String) extends ServiceTaskIntOptionBase
case class ServiceTaskBooleanOption(id: String, value: Boolean, optionTypeId: String) extends ServiceTaskBooleanOptionBase
case class ServiceTaskDoubleOption(id: String, value: Double, optionTypeId: String) extends ServiceTaskDoubleOptionBase
case class ServiceTaskFloatOption(id: String, value: Float, optionTypeId: String) extends ServiceTaskFloatOptionBase

// "Resolvable" Service Job Options. These will get transformed into PbSmrtPipeOptions
// These are also used by the mock-pbsmrtpipe job options
case class PbSmrtPipeServiceOptions(name: String,
                                    pipelineId: String,
                                    entryPoints: Seq[BoundServiceEntryPoint],
                                    taskOptions: Seq[ServiceTaskOptionBase],
                                    workflowOptions: Seq[ServiceTaskOptionBase])


// New DataSet Service Models
trait IdAble {
  val id: Int
  val uuid: UUID
}

trait ProjectAble {
  val userId: Int
  val jobId: Int
  val projectId: Int
  val isActive: Boolean
}

case class DataSetMetaDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime, updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, tags: String, version: String, comments: String, md5: String, userId: Int, jobId: Int, projectId: Int, isActive: Boolean) extends IdAble with ProjectAble

case class SubreadServiceSet(id: Int, uuid: UUID, cellId: String, metadataContextId: String, wellSampleName: String, wellName: String, bioSampleName: String, cellIndex: Int, instrumentId: String, instrumentName: String, runName: String, instrumentControlVersion: String) extends IdAble

case class SubreadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: SubreadServiceSet)

case class HdfSubreadServiceSet(id: Int, uuid: UUID, cellId: String, metadataContextId: String, wellSampleName: String, wellName: String, bioSampleName: String, cellIndex: Int, instrumentId: String, instrumentName: String, runName: String, instrumentControlVersion: String) extends IdAble

case class HdfSubreadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: HdfSubreadServiceSet)

case class ReferenceServiceSet(id: Int, uuid: UUID, ploidy: String, organism: String) extends IdAble

case class ReferenceServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ReferenceServiceSet)

case class AlignmentServiceSet(id: Int, uuid: UUID) extends IdAble

case class AlignmentServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: AlignmentServiceSet)

case class BarcodeServiceSet(id: Int, uuid: UUID) extends IdAble

case class BarcodeServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: BarcodeServiceSet)

case class CCSreadServiceSet(id: Int, uuid: UUID) extends IdAble

case class CCSreadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: CCSreadServiceSet)


// This is essentially just a flattening of the DataStoreJobFile + metadata specific to the
// /datastore-files endpoint
case class DataStoreServiceFile(uuid: UUID,
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
                                description: String)

// Files that have Reports
case class DataStoreReportFile(dataStoreFile: DataStoreServiceFile,
                               reportTypeId: String)

//DataSet
case class ServiceDataSetMetaType(id: String,
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
    val userId: Int
    val jobId: Int
    val projectId: Int
}

case class SubreadServiceDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime,
                                 updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, version: String,
                                 comments: String, tags: String, md5: String, instrumentName: String,
                                 metadataContextId: String, wellSampleName: String, wellName: String,
                                 bioSampleName: String,
                                 cellIndex: Int, runName: String, userId: Int, jobId: Int, projectId: Int,
                                 datasetType: String = Subread.toString())
  extends ServiceDataSetMetadata

case class HdfSubreadServiceDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime,
                                    updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, version: String,
                                    comments: String, tags: String, md5: String, instrumentName: String,
                                    metadataContextId: String, wellSampleName: String, wellName: String,
                                    bioSampleName: String, cellIndex: Int, runName: String, userId: Int, jobId: Int,
                                    projectId: Int,
                                    datasetType: String = HdfSubread.toString())
  extends ServiceDataSetMetadata

case class ReferenceServiceDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime,
                                   updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, version: String,
                                   comments: String, tags: String, md5: String, userId: Int, jobId: Int, projectId: Int,
                                   ploidy: String, organism: String,
                                   datasetType: String = Reference.toString())
  extends ServiceDataSetMetadata

case class AlignmentServiceDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime,
                                   updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, version: String,
                                   comments: String, tags: String, md5: String, userId: Int, jobId: Int, projectId: Int,
                                   datasetType: String = Alignment.toString())
  extends ServiceDataSetMetadata

case class CCSreadServiceDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime,
                                 updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, version: String,
                                 comments: String, tags: String, md5: String, userId: Int, jobId: Int, projectId: Int,
                                 datasetType: String = CCS.toString())
  extends ServiceDataSetMetadata

case class BarcodeServiceDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime,
                                 updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, version: String,
                                 comments: String, tags: String, md5: String, userId: Int, jobId: Int, projectId: Int,
                                 datasetType: String = Barcode.toString())
  extends ServiceDataSetMetadata

case class ContigServiceDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime,
                                updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, version: String,
                                comments: String, tags: String, md5: String, userId: Int, jobId: Int, projectId: Int,
                                datasetType: String = Contig.toString())
  extends ServiceDataSetMetadata


// Options used for Merging Datasets
case class DataSetMergeServiceOptions(datasetType: String, ids: Seq[Int], name: String)

case class Project(id: Int,
                   name: String,
                   description: String,
                   state: String,
                   createdAt: JodaDateTime,
                   updatedAt: JodaDateTime)

case class ProjectUser(projectId: Int, login: String, role: String)

case class ProjectRequest(name: String, state: String, description: String)

case class ProjectUserRequest(login: String, role: String)
case class ProjectUserResponse(user: UserResponse, role: String)

case class UserProjectResponse(role: Option[String], project: Project)
 
case class ProjectDatasetResponse(project: Project, dataset: DataSetMetaDataSet, role: Option[String])

// Some endpoints were originally implemented to return string-typed
// responses, but the smrt-link client has been sending an Accept:
// application/json header for all requests.  With that request
// header, the server was responding with a 406 for the
// string-response-typed endpoints.  Those string-returning endpoints
// were mostly returning success/failure messages, so they can use
// this class instead to return a json-typed message response.
case class MessageResponse(message: String)
