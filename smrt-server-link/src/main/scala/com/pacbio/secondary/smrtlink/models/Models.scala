package com.pacbio.secondary.smrtlink.models

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.http.scaladsl.model.Uri
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.semver.SemVersion
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import org.joda.time.{DateTime => JodaDateTime}
import com.pacificbiosciences.pacbiobasedatamodel.{
  SupportedAcquisitionStates,
  SupportedChipTypes,
  SupportedRunStates
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes._
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import spray.json.JsObject

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

object Models

object PacBioNamespaces {

  sealed trait PacBioNamespace { val name: String }
  // Commandline Tools, e.g., blasr
  case object SMRTTools extends PacBioNamespace { val name = "tools" }
  // Web Services, e.g., smrtlink_analysis
  case object SMRTServices extends PacBioNamespace { val name = "services" }
  // UI Applications, e.g., smrtlink_ui
  case object SMRTApps extends PacBioNamespace { val name = "apps" }

}

/**
  * Core SMRT Server Error model that is returned in failed requests
  *
  * @param httpCode  HTTP Code
  * @param message   detail message
  * @param errorType Terse error message type description
  */
case class ThrowableResponse(httpCode: Int, message: String, errorType: String) {
  def toLogMessage(): String =
    s"Request rejected: $httpCode - $errorType - $message"
}

object LogLevels {

  sealed abstract class LogLevel

  case object TRACE extends LogLevel
  case object DEBUG extends LogLevel
  case object INFO extends LogLevel
  case object WARN extends LogLevel
  case object ERROR extends LogLevel
  case object CRITICAL extends LogLevel
  case object FATAL extends LogLevel

  val ALL = Seq(TRACE, DEBUG, INFO, WARN, ERROR, CRITICAL, FATAL)

  // Allow some slop with case-ing here
  def fromString(sx: String): Option[LogLevel] =
    ALL.map(x => x.toString.toLowerCase -> x).toMap.get(sx.toLowerCase)

}

// Subsystem Settings
case class SubsystemConfig(id: String, name: String, startedAt: JodaDateTime)

case class PacBioComponentManifest(id: String,
                                   name: String,
                                   version: String,
                                   description: String,
                                   dependencies: Seq[String] = Nil)

case class ServiceComponent(id: String, typeId: String, version: String)

// Not sure what this should be. Is this the subsystem config?
case class ServerConfig(id: String, version: String)

case class ServiceStatus(id: String,
                         message: String,
                         uptime: Long,
                         uuid: UUID,
                         version: String,
                         user: String)

// Alarm System
object AlarmSeverity {
  sealed class AlarmSeverity(val severity: Int)
      extends Ordered[AlarmSeverity] {
    def compare(s2: AlarmSeverity): Int = severity compareTo s2.severity
  }

  case object CLEAR extends AlarmSeverity(severity = 0)
  case object WARN extends AlarmSeverity(severity = 1)
  case object ERROR extends AlarmSeverity(severity = 2)
  case object CRITICAL extends AlarmSeverity(severity = 3)
  case object FATAL extends AlarmSeverity(severity = 4)
  case object FATAL_IMMEDIATE extends AlarmSeverity(severity = 5)

  val ALL = Seq(CLEAR, WARN, ERROR, CRITICAL, FATAL, FATAL_IMMEDIATE)
  val alarmSeverityByName = ALL.map(x => x.toString -> x).toMap
  val nameByAlarmSeverity = ALL.map(x => x -> x.toString).toMap
}

case class Alarm(id: String, name: String, description: String)

case class AlarmUpdate(value: Double,
                       message: Option[String],
                       severity: AlarmSeverity.AlarmSeverity)

case class AlarmStatus(id: String,
                       value: Double,
                       message: Option[String],
                       severity: AlarmSeverity.AlarmSeverity,
                       updatedAt: JodaDateTime)

// Logging System
case class LogResourceRecord(description: String, id: String, name: String)

case class LogMessageRecord(message: String,
                            level: LogLevels.LogLevel,
                            sourceId: String)

// Users

/**
  * User data model
  *
  * @param userId  User "Id" Example: mskinner This this user primary key that is used in the SL db.
  * @param userEmail User email address (This is a bit odd. The CLAIM is required, but it's optional here)
  * @param firstName User first name
  * @param lastName  User last name
  * @param roles     Roles of the specific user
  *
  * MK. I strongly believe that email address should have been used as the primary key as the user id.
  *
  * Currently, this is creating the "userId" from the "enduser" wso2 claim. See the JwtUtils for details
  */
case class UserRecord(userId: String,
                      userEmail: Option[String] = None,
                      firstName: Option[String] = None,
                      lastName: Option[String] = None,
                      roles: Set[String] = Set.empty) {

  def getDisplayName: String = {
    val name = for {
      f <- firstName
      l <- lastName
    } yield s"$f $l"

    name.getOrElse(userId)
  }
}

// Files Service
case class DirectoryResource(fullPath: String,
                             subDirectories: Seq[DirectoryResource],
                             files: Seq[FileResource])
case class FileResource(fullPath: String,
                        name: String,
                        mimeType: String,
                        sizeInBytes: Long,
                        sizeReadable: String)
case class DiskSpaceResource(fullPath: String,
                             totalSpace: Long,
                             freeSpace: Long)

// SubSystem Resources
case class SubsystemResource(uuid: UUID,
                             name: String,
                             version: String,
                             url: String,
                             apiDocs: String,
                             userDocs: String,
                             createdAt: JodaDateTime,
                             updatedAt: JodaDateTime)
// Record is what a user would POST
case class SubsystemResourceRecord(name: String,
                                   version: String,
                                   url: String,
                                   apiDocs: String,
                                   userDocs: String)

// Runs

case class RunCreate(dataModel: String)

case class RunUpdate(dataModel: Option[String] = None,
                     reserved: Option[Boolean] = None)

case class RunSummary(uniqueId: UUID,
                      name: String,
                      summary: Option[String],
                      createdBy: Option[String],
                      createdAt: Option[JodaDateTime],
                      startedAt: Option[JodaDateTime],
                      transfersCompletedAt: Option[JodaDateTime],
                      completedAt: Option[JodaDateTime],
                      status: SupportedRunStates,
                      chipType: SupportedChipTypes,
                      totalCells: Int,
                      numCellsCompleted: Int,
                      numCellsFailed: Int,
                      instrumentName: Option[String],
                      instrumentSerialNumber: Option[String],
                      instrumentSwVersion: Option[String],
                      primaryAnalysisSwVersion: Option[String],
                      chemistrySwVersion: Option[String],
                      context: Option[String],
                      terminationInfo: Option[String],
                      reserved: Boolean,
                      numStandardCells: Int,
                      numLRCells: Int,
                      multiJobId: Option[Int] = None) {

  def withDataModel(dataModel: String) =
    Run(
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
      chipType,
      totalCells,
      numCellsCompleted,
      numCellsFailed,
      instrumentName,
      instrumentSerialNumber,
      instrumentSwVersion,
      primaryAnalysisSwVersion,
      chemistrySwVersion,
      context,
      terminationInfo,
      reserved,
      numStandardCells,
      numLRCells,
      multiJobId
    )
}

case class Run(dataModel: String,
               uniqueId: UUID,
               name: String,
               summary: Option[String],
               createdBy: Option[String],
               createdAt: Option[JodaDateTime],
               startedAt: Option[JodaDateTime],
               transfersCompletedAt: Option[JodaDateTime],
               completedAt: Option[JodaDateTime],
               status: SupportedRunStates,
               chipType: SupportedChipTypes,
               totalCells: Int,
               numCellsCompleted: Int,
               numCellsFailed: Int,
               instrumentName: Option[String],
               instrumentSerialNumber: Option[String],
               instrumentSwVersion: Option[String],
               primaryAnalysisSwVersion: Option[String],
               chemistrySwVersion: Option[String],
               context: Option[String],
               terminationInfo: Option[String],
               reserved: Boolean,
               numStandardCells: Int,
               numLRCells: Int,
               multiJobId: Option[Int]) {

  def summarize =
    RunSummary(
      uniqueId,
      name,
      summary,
      createdBy,
      createdAt,
      startedAt,
      transfersCompletedAt,
      completedAt,
      status,
      chipType,
      totalCells,
      numCellsCompleted,
      numCellsFailed,
      instrumentName,
      instrumentSerialNumber,
      instrumentSwVersion,
      primaryAnalysisSwVersion,
      chemistrySwVersion,
      context,
      terminationInfo,
      reserved,
      numStandardCells,
      numLRCells,
      multiJobId
    )
}

case class CollectionMetadata(runId: UUID,
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
                              terminationInfo: Option[String],
                              cellType: Option[String])

// Samples

case class Sample(details: String,
                  uniqueId: UUID,
                  name: String,
                  createdBy: String,
                  createdAt: JodaDateTime)

case class SampleCreate(details: String, uniqueId: UUID, name: String)

case class SampleUpdate(details: Option[String], name: Option[String])

case class SampleTestExamples(count: Int)

// Registry

case class RegistryResource(createdAt: JodaDateTime,
                            uuid: UUID,
                            host: String,
                            port: Int,
                            resourceId: String,
                            updatedAt: JodaDateTime)

case class RegistryResourceCreate(host: String, port: Int, resourceId: String)

case class RegistryResourceUpdate(host: Option[String], port: Option[Int])

case class RegistryProxyRequest(path: String,
                                method: String,
                                data: Option[Array[Byte]],
                                headers: Option[Map[String, String]],
                                params: Option[Map[String, String]])

// Jobs

// This is terse Status message used in sub-component endpoints root/my-endpoints/status
case class SimpleStatus(id: String, msg: String, uptime: Long)

// This is a little wrong to generalize by Job type, each job instance needs to have a isQuick (e.g., Fasta converter).
case class JobTypeEndPoint(jobTypeId: String,
                           description: String,
                           isQuick: Boolean,
                           isMultiJob: Boolean)

// Entry point use to create jobs from the Service layer. This will then be translated to a
// BoundEntryPoint with the resolved path of the DataSet
case class BoundServiceEntryPoint(entryId: String,
                                  fileTypeId: String,
                                  datasetId: IdAble)

// Entry points that are have dataset types
case class EngineJobEntryPoint(jobId: Int,
                               datasetUUID: UUID,
                               datasetType: String) {
  def toRecord = EngineJobEntryPointRecord(datasetUUID, datasetType)
}

//FIXME(mpkocher)(8-22-2017) The dataset metatype needs to be a proper type
case class EngineJobEntryPointRecord(datasetUUID: UUID, datasetType: String)

// Service related Job Tasks

/**
  * Service Request Format to create a TaskJob
  *
  * @param uuid Globally Unique Task UUID
  * @param taskId task id which is lo
  * @param taskTypeId task type (i.e., tool contract type id)
  * @param name Display name of the task
  * @param createdAt Time when the task was created
  */
case class CreateJobTaskRecord(uuid: UUID,
                               taskId: String,
                               taskTypeId: String,
                               name: String,
                               createdAt: JodaDateTime)

case class UpdateJobTaskRecord(uuid: UUID,
                               state: String,
                               message: String,
                               errorMessage: Option[String])

case class UpdateJobRecord(name: Option[String],
                           comment: Option[String],
                           tags: Option[String])

case class JobCompletedMessage(job: EngineJob)
case class MultiJobSubmitted(jobId: Int)

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

case class DataSetMetaDataSet(id: Int,
                              uuid: UUID,
                              name: String,
                              path: String,
                              createdAt: JodaDateTime,
                              updatedAt: JodaDateTime,
                              importedAt: JodaDateTime,
                              numRecords: Long,
                              totalLength: Long,
                              tags: String,
                              version: String,
                              comments: String,
                              md5: String,
                              createdBy: Option[String],
                              jobId: Int,
                              projectId: Int,
                              isActive: Boolean,
                              parentUuid: Option[UUID],
                              numChildren: Int = 0)
    extends UniqueIdAble
    with ProjectAble

case class SubreadServiceSet(id: Int,
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
                             instrumentControlVersion: String,
                             dnaBarcodeName: Option[String])
    extends UniqueIdAble

case class HdfSubreadServiceSet(id: Int,
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
                                instrumentControlVersion: String)
    extends UniqueIdAble

case class HdfSubreadServiceMetaDataSet(metadata: DataSetMetaDataSet,
                                        dataset: HdfSubreadServiceSet)

case class ReferenceServiceSet(id: Int,
                               uuid: UUID,
                               ploidy: String,
                               organism: String)
    extends UniqueIdAble

case class ReferenceServiceMetaDataSet(metadata: DataSetMetaDataSet,
                                       dataset: ReferenceServiceSet)

case class AlignmentServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class AlignmentServiceMetaDataSet(metadata: DataSetMetaDataSet,
                                       dataset: AlignmentServiceSet)

case class BarcodeServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class BarcodeServiceMetaDataSet(metadata: DataSetMetaDataSet,
                                     dataset: BarcodeServiceSet)

case class ConsensusReadServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class ConsensusReadServiceMetaDataSet(metadata: DataSetMetaDataSet,
                                           dataset: ConsensusReadServiceSet)

case class GmapReferenceServiceSet(id: Int,
                                   uuid: UUID,
                                   ploidy: String,
                                   organism: String)
    extends UniqueIdAble

case class GmapReferenceServiceMetaDataSet(metadata: DataSetMetaDataSet,
                                           dataset: GmapReferenceServiceSet)

case class ConsensusAlignmentServiceSet(id: Int, uuid: UUID)
    extends UniqueIdAble

case class ConsensusAlignmentServiceMetaDataSet(
    metadata: DataSetMetaDataSet,
    dataset: ConsensusAlignmentServiceSet)

case class ContigServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class ContigServiceMetaDataSet(metadata: DataSetMetaDataSet,
                                    dataset: ContigServiceSet)

case class TranscriptServiceSet(id: Int, uuid: UUID) extends UniqueIdAble
case class TranscriptServiceMetaDataSet(metadata: DataSetMetaDataSet,
                                        dataset: TranscriptServiceSet)

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
                                description: String,
                                isActive: Boolean = true) {

  def fileExists: Boolean = Paths.get(path).toFile.exists
}

// Files that have Reports
case class DataStoreReportFile(dataStoreFile: DataStoreServiceFile,
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
  val importedAt: JodaDateTime
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
  val parentUuid: Option[UUID]
  val isActive: Boolean
  val numChildren: Int

  // MK. I'm not sure this is a good idea.
  def toDataStoreFile(sourceId: String,
                      fileType: FileTypes.FileType,
                      fileSize: Long = 0L,
                      isChunked: Boolean = false) =
    DataStoreFile(uuid,
                  sourceId,
                  fileType.fileTypeId,
                  fileSize,
                  createdAt,
                  createdAt,
                  path,
                  isChunked,
                  name,
                  "description")
}

case class SubreadServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    importedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    instrumentName: String,
    instrumentControlVersion: String,
    metadataContextId: String,
    wellSampleName: String,
    wellName: String,
    bioSampleName: String,
    cellIndex: Int,
    cellId: String, // Barcode from <pbmeta:CellPac Barcode="BA010713635979244259265850" ExpirationDate="2016-11-04" LotNumber="320296" PartNumber="100-512-700"/>
    runName: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    dnaBarcodeName: Option[String],
    parentUuid: Option[UUID],
    isActive: Boolean = true,
    numChildren: Int = 0,
    datasetType: String = Subread.toString())
    extends ServiceDataSetMetadata

case class HdfSubreadServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    importedAt: JodaDateTime,
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
    parentUuid: Option[UUID] = None,
    isActive: Boolean = true,
    numChildren: Int = 0,
    datasetType: String = HdfSubread.toString())
    extends ServiceDataSetMetadata

case class ReferenceServiceDataSet(id: Int,
                                   uuid: UUID,
                                   name: String,
                                   path: String,
                                   createdAt: JodaDateTime,
                                   updatedAt: JodaDateTime,
                                   importedAt: JodaDateTime,
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
                                   parentUuid: Option[UUID] = None,
                                   isActive: Boolean = true,
                                   numChildren: Int = 0,
                                   datasetType: String = Reference.toString())
    extends ServiceDataSetMetadata

case class AlignmentServiceDataSet(id: Int,
                                   uuid: UUID,
                                   name: String,
                                   path: String,
                                   createdAt: JodaDateTime,
                                   updatedAt: JodaDateTime,
                                   importedAt: JodaDateTime,
                                   numRecords: Long,
                                   totalLength: Long,
                                   version: String,
                                   comments: String,
                                   tags: String,
                                   md5: String,
                                   createdBy: Option[String],
                                   jobId: Int,
                                   projectId: Int,
                                   parentUuid: Option[UUID] = None,
                                   isActive: Boolean = true,
                                   numChildren: Int = 0,
                                   datasetType: String = Alignment.toString())
    extends ServiceDataSetMetadata

case class ConsensusReadServiceDataSet(id: Int,
                                       uuid: UUID,
                                       name: String,
                                       path: String,
                                       createdAt: JodaDateTime,
                                       updatedAt: JodaDateTime,
                                       importedAt: JodaDateTime,
                                       numRecords: Long,
                                       totalLength: Long,
                                       version: String,
                                       comments: String,
                                       tags: String,
                                       md5: String,
                                       createdBy: Option[String],
                                       jobId: Int,
                                       projectId: Int,
                                       parentUuid: Option[UUID] = None,
                                       isActive: Boolean = true,
                                       numChildren: Int = 0,
                                       datasetType: String = CCS.toString())
    extends ServiceDataSetMetadata

case class ConsensusAlignmentServiceDataSet(id: Int,
                                            uuid: UUID,
                                            name: String,
                                            path: String,
                                            createdAt: JodaDateTime,
                                            updatedAt: JodaDateTime,
                                            importedAt: JodaDateTime,
                                            numRecords: Long,
                                            totalLength: Long,
                                            version: String,
                                            comments: String,
                                            tags: String,
                                            md5: String,
                                            createdBy: Option[String],
                                            jobId: Int,
                                            projectId: Int,
                                            parentUuid: Option[UUID] = None,
                                            isActive: Boolean = true,
                                            numChildren: Int = 0,
                                            datasetType: String =
                                              AlignmentCCS.toString())
    extends ServiceDataSetMetadata

case class BarcodeServiceDataSet(id: Int,
                                 uuid: UUID,
                                 name: String,
                                 path: String,
                                 createdAt: JodaDateTime,
                                 updatedAt: JodaDateTime,
                                 importedAt: JodaDateTime,
                                 numRecords: Long,
                                 totalLength: Long,
                                 version: String,
                                 comments: String,
                                 tags: String,
                                 md5: String,
                                 createdBy: Option[String],
                                 jobId: Int,
                                 projectId: Int,
                                 parentUuid: Option[UUID] = None,
                                 isActive: Boolean = true,
                                 numChildren: Int = 0,
                                 datasetType: String = Barcode.toString())
    extends ServiceDataSetMetadata

case class ContigServiceDataSet(id: Int,
                                uuid: UUID,
                                name: String,
                                path: String,
                                createdAt: JodaDateTime,
                                updatedAt: JodaDateTime,
                                importedAt: JodaDateTime,
                                numRecords: Long,
                                totalLength: Long,
                                version: String,
                                comments: String,
                                tags: String,
                                md5: String,
                                createdBy: Option[String],
                                jobId: Int,
                                projectId: Int,
                                parentUuid: Option[UUID] = None,
                                isActive: Boolean = true,
                                numChildren: Int = 0,
                                datasetType: String = Contig.toString())
    extends ServiceDataSetMetadata

case class GmapReferenceServiceDataSet(id: Int,
                                       uuid: UUID,
                                       name: String,
                                       path: String,
                                       createdAt: JodaDateTime,
                                       updatedAt: JodaDateTime,
                                       importedAt: JodaDateTime,
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
                                       parentUuid: Option[UUID] = None,
                                       isActive: Boolean = true,
                                       numChildren: Int = 0,
                                       datasetType: String =
                                         GmapReference.toString())
    extends ServiceDataSetMetadata

case class TranscriptServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    importedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    parentUuid: Option[UUID] = None,
    isActive: Boolean = true,
    numChildren: Int = 0,
    datasetType: String = Transcript.toString())
    extends ServiceDataSetMetadata

/**
  * Subset of Grammar defined here: https://specs.openstack.org/openstack/api-wg/guidelines/pagination_filter_sort.html
  *
  * GET /app/items?foo=buzz
  * GET /app/items?foo=in:buzz,bar
  * GET /app/items?size=gt:8
  *
  */
object QueryOperators {

  // For queries that need db.filter(_.x === null)
  val NULL = "null"

  /**
    * Common Utils to generate a
    */
  private def toInQuery(values: Set[String]): String =
    s"in:${values.toList.reduce(_ + "," + _)}"

  private def toEqQuery(value: String): String = value
  private def toLtQuery(value: String): String = s"lt:$value"
  private def toLteQuery(value: String) = s"lte:$value"
  private def toGtQuery(value: String) = s"gt:$value"
  private def toGteQuery(value: String) = s"gte:$value"
  private def toMatchQuery(value: String) = s"like:$value"

  // This is a bit of a grab bag of utils
  trait QueryOperatorConverter[T] {

    /**
      * Convert the raw string to the specific type of Query Operator
      */
    def convertFromString(sx: String): T
    def convertFromStringToSet(sx: String): Set[T] =
      sx.split(",").map(convertFromString).toSet

    def toValue(sx: String): Option[T] = Try(convertFromString(sx)).toOption
    def toSetValue(sx: String): Option[Set[T]] =
      Try(convertFromStringToSet(sx)).toOption

  }

  // Base Query Interface type
  trait QuertyOperator {

    /**
      * Returns the raw Query string. e.g, in:1,2,3 or gt:12354
      */
    def toQueryString: String
  }

  sealed trait StringQueryOperator extends QuertyOperator
  case class StringEqQueryOperator(value: String) extends StringQueryOperator {
    override def toQueryString: String = toEqQuery(value)
  }
  case class StringMatchQueryOperator(value: String)
      extends StringQueryOperator {
    override def toQueryString: String = toMatchQuery(value)
  }
  case class StringInQueryOperator(value: Set[String])
      extends StringQueryOperator {
    override def toQueryString: String = toInQuery(value)
  }

  object StringQueryOperator extends QueryOperatorConverter[String] {

    override def convertFromString(sx: String): String = sx

    /**
      * NOTE, the string should already be URL Decoded!
      *
      * foo=bar
      * foo=in:bar,baz
      *
      * @param value raw Query String
      * @return
      */
    def fromString(value: String): Option[StringQueryOperator] = {
      value.split(":", 2).toList match {
        case "in" :: tail :: Nil => toSetValue(tail).map(StringInQueryOperator)
        case "like" :: tail :: Nil =>
          toValue(tail).map(StringMatchQueryOperator)
        case head :: Nil => toValue(head).map(StringEqQueryOperator)
        case _ =>
          // Invalid or unsupported String Query Operator
          None
      }
    }
  }

  // For Numeric Types
  sealed trait QueryOperator[T]

  sealed trait IntQueryOperator extends QueryOperator[Int] with QuertyOperator
  case class IntEqQueryOperator(value: Int) extends IntQueryOperator {
    override def toQueryString: String = value.toString
  }
  case class IntGtQueryOperator(value: Int) extends IntQueryOperator {
    override def toQueryString: String = toGtQuery(value.toString)
  }
  case class IntGteQueryOperator(value: Int) extends IntQueryOperator {
    override def toQueryString: String = toGteQuery(value.toString)
  }
  case class IntLtQueryOperator(value: Int) extends IntQueryOperator {
    override def toQueryString: String = toLtQuery(value.toString)
  }
  case class IntLteQueryOperator(value: Int) extends IntQueryOperator {
    override def toQueryString: String = value.toString
  }
  case class IntInQueryOperator(value: Set[Int]) extends IntQueryOperator {
    override def toQueryString: String = toInQuery(value.map(_.toString))
  }

  object IntQueryOperator extends QueryOperatorConverter[Int] {
    def convertFromString(sx: String): Int = sx.toInt

    def fromString(value: String): Option[IntQueryOperator] = {
      value.split(":", 2).toList match {
        case head :: Nil => toValue(head).map(IntEqQueryOperator)
        case "in" :: tail :: Nil => toSetValue(tail).map(IntInQueryOperator)
        case "gt" :: tail :: Nil => toValue(tail).map(IntGtQueryOperator)
        case "gte" :: tail :: Nil => toValue(tail).map(IntGteQueryOperator)
        case "lt" :: tail :: Nil => toValue(tail).map(IntLtQueryOperator)
        case "lte" :: tail :: Nil => toValue(tail).map(IntLteQueryOperator)
        case _ => None
      }
    }
  }

  sealed trait LongQueryOperator
      extends QueryOperator[Long]
      with QuertyOperator
  case class LongEqQueryOperator(value: Long) extends LongQueryOperator {
    override def toQueryString: String = value.toString
  }
  case class LongGtQueryOperator(value: Long) extends LongQueryOperator {
    override def toQueryString: String = toGtQuery(value.toString)
  }
  case class LongGteQueryOperator(value: Long) extends LongQueryOperator {
    override def toQueryString: String = toGteQuery(value.toString)
  }
  case class LongLtQueryOperator(value: Long) extends LongQueryOperator {
    override def toQueryString: String = toLtQuery(value.toString)
  }
  case class LongLteQueryOperator(value: Long) extends LongQueryOperator {
    override def toQueryString: String = toLteQuery(value.toString)
  }
  case class LongInQueryOperator(value: Set[Long]) extends LongQueryOperator {
    override def toQueryString: String = toInQuery(value.map(_.toString))
  }

  object LongQueryOperator extends QueryOperatorConverter[Long] {
    def convertFromString(sx: String): Long = sx.toLong

    def fromString(value: String): Option[LongQueryOperator] = {
      value.split(":", 2).toList match {
        case head :: Nil => toValue(head).map(LongEqQueryOperator)
        case "in" :: tail :: Nil => toSetValue(tail).map(LongInQueryOperator)
        case "gt" :: tail :: Nil => toValue(tail).map(LongGtQueryOperator)
        case "gte" :: tail :: Nil => toValue(tail).map(LongGteQueryOperator)
        case "lt" :: tail :: Nil => toValue(tail).map(LongLtQueryOperator)
        case "lte" :: tail :: Nil => toValue(tail).map(LongLteQueryOperator)
        case _ => None
      }
    }
  }

  sealed trait DateTimeQueryOperator extends QuertyOperator {
    def dateTimeToString(dt: JodaDateTime): String = dt.toString()
  }
  case class DateTimeEqOperator(dt: JodaDateTime)
      extends DateTimeQueryOperator {
    override def toQueryString: String = dateTimeToString(dt)
  }
  case class DateTimeGtOperator(dt: JodaDateTime)
      extends DateTimeQueryOperator {
    override def toQueryString: String = toGteQuery(dateTimeToString(dt))
  }
  case class DateTimeGteOperator(dt: JodaDateTime)
      extends DateTimeQueryOperator {
    override def toQueryString: String = toGteQuery(dateTimeToString(dt))
  }
  case class DateTimeLtOperator(dt: JodaDateTime)
      extends DateTimeQueryOperator {
    override def toQueryString: String = toLtQuery(dateTimeToString(dt))
  }
  case class DateTimeLteOperator(dt: JodaDateTime)
      extends DateTimeQueryOperator {
    override def toQueryString: String = toLteQuery(dateTimeToString(dt))
  }

  object DateTimeQueryOperator extends QueryOperatorConverter[JodaDateTime] {
    def convertFromString(sx: String): JodaDateTime = JodaDateTime.parse(sx)

    // "in" doesn't really make sense here. It would be better
    // to define an interval of "createdAt=range:start,end"
    // For now, the core gt, gte, lt, lte the core usecases.
    // A user could make a query with one then filter client side,
    // or make two queries and filter
    def fromString(value: String): Option[DateTimeQueryOperator] = {
      value.split(":", 2).toList match {
        case head :: Nil => toValue(head).map(DateTimeEqOperator)
        case "gt" :: tail :: Nil => toValue(tail).map(DateTimeGtOperator)
        case "gte" :: tail :: Nil => toValue(tail).map(DateTimeGteOperator)
        case "lt" :: tail :: Nil => toValue(tail).map(DateTimeLtOperator)
        case "lte" :: tail :: Nil => toValue(tail).map(DateTimeLteOperator)
        case _ => None
      }
    }
  }

  sealed trait UUIDQueryOperator extends QuertyOperator {
    def uuidToString(uuid: UUID): String = uuid.toString
  }
  case class UUIDEqOperator(uuid: UUID) extends UUIDQueryOperator {
    override def toQueryString: String = uuidToString(uuid)
  }
  case class UUIDInOperator(uuids: Set[UUID]) extends UUIDQueryOperator {
    override def toQueryString: String = toInQuery(uuids.map(uuidToString))
  }

  object UUIDQueryOperator extends QueryOperatorConverter[UUID] {
    override def convertFromString(sx: String): UUID = UUID.fromString(sx)

    def fromString(sx: String): Option[UUIDQueryOperator] = {
      sx.split(":", 2).toList match {
        case "in" :: tail :: Nil => toSetValue(tail).map(UUIDInOperator)
        case head :: Nil => toValue(head).map(UUIDEqOperator)
        case _ => None
      }
    }
  }

  // It's not completely clear to me how to extend UUIDQueryOperator
  sealed trait UUIDOptionQueryOperator extends QuertyOperator {
    def uuidToString(uuid: UUID): String = uuid.toString
  }

  case class UUIDOptionEqOperator(uuid: UUID) extends UUIDOptionQueryOperator {
    override def toQueryString: String = uuidToString(uuid)
  }
  case class UUIDOptionInOperator(uuids: Set[UUID])
      extends UUIDOptionQueryOperator {
    override def toQueryString: String = toInQuery(uuids.map(uuidToString))
  }

  case class UUIDNullQueryOperator() extends UUIDOptionQueryOperator {
    override def toQueryString: String = toEqQuery(NULL)
  }

  object UUIDOptionQueryOperator extends QueryOperatorConverter[UUID] {
    override def convertFromString(sx: String): UUID = UUID.fromString(sx)

    def fromString(sx: String): Option[UUIDOptionQueryOperator] = {
      sx.split(":", 2).toList match {
        case "in" :: tail :: Nil => toSetValue(tail).map(UUIDOptionInOperator)
        case "null" :: Nil => Some(UUIDNullQueryOperator())
        case head :: Nil => toValue(head).map(UUIDOptionEqOperator)
        case _ => None
      }
    }
  }

  sealed trait JobStateQueryOperator extends QuertyOperator {
    def jobStateToString(state: AnalysisJobStates.JobStates): String =
      state.toString
  }

  case class JobStateEqOperator(state: AnalysisJobStates.JobStates)
      extends JobStateQueryOperator {
    override def toQueryString: String = jobStateToString(state)
  }
  case class JobStateInOperator(states: Set[AnalysisJobStates.JobStates])
      extends JobStateQueryOperator {
    override def toQueryString: String =
      toInQuery(states.map(jobStateToString))
  }

  object JobStateQueryOperator
      extends QueryOperatorConverter[AnalysisJobStates.JobStates] {
    override def convertFromString(sx: String): AnalysisJobStates.JobStates =
      AnalysisJobStates.toState(sx).getOrElse(AnalysisJobStates.UNKNOWN)

    def fromString(value: String): Option[JobStateQueryOperator] = {
      value.split(":", 2).toList match {
        case "in" :: tail :: Nil => toSetValue(tail).map(JobStateInOperator)
        case head :: Nil => toValue(head).map(JobStateEqOperator)
        case _ =>
          // Invalid or unsupported String Query Operator
          None
      }
    }
  }
}

trait SearchCriteriaBase {
  val isActive: Option[Boolean]
  val limit: Int
  val marker: Option[Int]

  def operators: Map[String, Option[String]]

  def toQuery: Uri.Query = {

    // This is a little goofy, Remove Optional values (as keys)
    def flattenMap(m: Map[String, Option[String]]): Map[String, String] = {
      m.map {
          case (k, v) =>
            v match {
              case Some(op) => Some((k, op))
              case _ => None
            }
        }
        .toList
        .flatten
        .toMap
    }

    // Need to handle the project ids
    val m2: Map[String, Option[String]] =
      Map(
        "limit" -> Some(limit.toString),
        "isActive" -> isActive.map(_.toString),
        "marker" -> marker.map(_.toString)
      )

    val ms = Seq(operators, m2).map(flattenMap).reduce(_ ++ _)

    val urlEncodedParmas =
      ms.mapValues(sx => java.net.URLEncoder.encode(sx, "utf-8"))

    Uri.Query(urlEncodedParmas)
  }
}

case class DataSetSearchCriteria(
    projectIds: Set[Int],
    isActive: Option[Boolean] = Some(true),
    limit: Int,
    marker: Option[Int] = None, // offset
    id: Option[QueryOperators.IntQueryOperator] = None,
    uuid: Option[QueryOperators.UUIDQueryOperator] = None,
    path: Option[QueryOperators.StringQueryOperator] = None,
    name: Option[QueryOperators.StringQueryOperator] = None,
    createdAt: Option[QueryOperators.DateTimeQueryOperator] = None,
    updatedAt: Option[QueryOperators.DateTimeQueryOperator] = None,
    importedAt: Option[QueryOperators.DateTimeQueryOperator] = None,
    numRecords: Option[QueryOperators.LongQueryOperator] = None,
    totalLength: Option[QueryOperators.LongQueryOperator] = None,
    version: Option[QueryOperators.StringQueryOperator] = None,
    createdBy: Option[QueryOperators.StringQueryOperator] = None,
    jobId: Option[QueryOperators.IntQueryOperator] = None,
    parentUuid: Option[QueryOperators.UUIDOptionQueryOperator] = None,
    projectId: Option[QueryOperators.IntQueryOperator] = None,
    numChildren: Option[QueryOperators.IntQueryOperator] = None)
    extends SearchCriteriaBase {

  /**
    * Get all Operators as a Map of values
    */
  def operators: Map[String, Option[String]] = {
    Map(
      "id" -> id.map(_.toQueryString),
      "uuid" -> uuid.map(_.toQueryString),
      "name" -> name.map(_.toQueryString),
      "path" -> path.map(_.toQueryString),
      "createdAt" -> createdAt.map(_.toQueryString),
      "updatedAt" -> updatedAt.map(_.toQueryString),
      "importedAt" -> importedAt.map(_.toQueryString),
      "numRecords" -> numRecords.map(_.toQueryString),
      "totalLength" -> totalLength.map(_.toQueryString),
      "version" -> version.map(_.toQueryString),
      "createdBy" -> createdBy.map(_.toQueryString),
      "jobId" -> jobId.map(_.toQueryString),
      "parentUuid" -> parentUuid.map(_.toQueryString),
      "projectId" -> projectId.map(_.toQueryString),
      "numChildren" -> numChildren.map(_.toQueryString)
    )
  }
}

object DataSetSearchCriteria {
  final val DEFAULT_MAX_DATASETS = 10000
  final val DEFAULT_IS_ACTIVE: Option[Boolean] = Some(true)

  def default =
    DataSetSearchCriteria(Set.empty[Int],
                          isActive = DEFAULT_IS_ACTIVE,
                          name = None,
                          limit = DEFAULT_MAX_DATASETS)
}

case class JobSearchCriteria(
    projectIds: Set[Int],
    limit: Int,
    marker: Option[Int] = None,
    isActive: Option[Boolean] = Some(true),
    id: Option[QueryOperators.IntQueryOperator] = None,
    uuid: Option[QueryOperators.UUIDQueryOperator] = None,
    name: Option[QueryOperators.StringQueryOperator] = None,
    comment: Option[QueryOperators.StringQueryOperator] = None,
    createdAt: Option[QueryOperators.DateTimeQueryOperator] = None,
    updatedAt: Option[QueryOperators.DateTimeQueryOperator] = None,
    jobUpdatedAt: Option[QueryOperators.DateTimeQueryOperator] = None,
    state: Option[QueryOperators.JobStateQueryOperator] = None,
    jobTypeId: Option[QueryOperators.StringQueryOperator] = None,
    path: Option[QueryOperators.StringQueryOperator] = None,
    createdBy: Option[QueryOperators.StringQueryOperator] = None,
    createdByEmail: Option[QueryOperators.StringQueryOperator] = None,
    smrtlinkVersion: Option[QueryOperators.StringQueryOperator] = None,
    errorMessage: Option[QueryOperators.StringQueryOperator] = None,
    projectId: Option[QueryOperators.IntQueryOperator] = None,
    isMultiJob: Option[Boolean] = None,
    parentMultiJobId: Option[QueryOperators.IntQueryOperator] = None,
    importedAt: Option[QueryOperators.DateTimeQueryOperator] = None,
    tags: Option[QueryOperators.StringQueryOperator] = None,
    subJobTypeId: Option[QueryOperators.StringQueryOperator] = None)
    extends SearchCriteriaBase {

  def withProject(projectId: Int) =
    copy(projectId = Some(QueryOperators.IntEqQueryOperator(projectId)))

  /**
    * Get all Operators as a Map of values
    */
  def operators: Map[String, Option[String]] = {
    Map(
      "id" -> id.map(_.toQueryString),
      "uuid" -> uuid.map(_.toQueryString),
      "name" -> name.map(_.toQueryString),
      "comment" -> comment.map(_.toQueryString),
      "createdAt" -> createdAt.map(_.toQueryString),
      "updatedAt" -> updatedAt.map(_.toQueryString),
      "jobUpdatedAt" -> jobUpdatedAt.map(_.toQueryString),
      "state" -> state.map(_.toQueryString),
      "jobTypeId" -> jobTypeId.map(_.toQueryString),
      "path" -> path.map(_.toQueryString),
      "createdBy" -> createdBy.map(_.toQueryString),
      "createdByEmail" -> createdByEmail.map(_.toQueryString),
      "smrtlinkVersion" -> path.map(_.toQueryString),
      "projectId" -> projectId.map(_.toQueryString),
//      "isMultiJob" -> isMultiJob.map(_.toQueryString), FIXME
      "parentMultiJobId" -> parentMultiJobId.map(_.toQueryString),
      "importedAt" -> importedAt.map(_.toQueryString),
      "tags" -> tags.map(_.toQueryString),
      "subJobTypeId" -> subJobTypeId.map(_.toQueryString)
    )
  }

}

object JobSearchCriteria {
  final val DEFAULT_MAX_JOBS = 6000
  final val DEFAULT_IS_ACTIVE: Option[Boolean] = Some(true)

  def default =
    JobSearchCriteria(Set.empty[Int],
                      isActive = DEFAULT_IS_ACTIVE,
                      name = None,
                      limit = DEFAULT_MAX_JOBS)
  // used for metrics harvesting
  def allAnalysisJobs =
    default.copy(
      jobTypeId =
        Some(QueryOperators.StringEqQueryOperator(JobTypeIds.PBSMRTPIPE.id)),
      isActive = None)
}

// Options used for Merging Datasets
// FIXME. This should use a DataSetMetaType, not String!
case class DataSetMergeServiceOptions(datasetType: String,
                                      ids: Seq[Int],
                                      name: String)

// ImportAble models for interfacing to the DAO

// Util container. This should be named better
case class DsServiceJobFile(file: DataStoreServiceFile,
                            createdBy: Option[String],
                            projectId: Int)

sealed trait ImportAbleServiceFile {
  val ds: DsServiceJobFile
}

case class ImportAbleDataStoreFile(ds: DsServiceJobFile)
    extends ImportAbleServiceFile

case class ImportAbleSubreadSet(ds: DsServiceJobFile,
                                file: SubreadServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleHdfSubreadSet(ds: DsServiceJobFile,
                                   file: HdfSubreadServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleAlignmentSet(ds: DsServiceJobFile,
                                  file: AlignmentServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleBarcodeSet(ds: DsServiceJobFile,
                                file: BarcodeServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleConsensusReadSet(ds: DsServiceJobFile,
                                      file: ConsensusReadServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleConsensusAlignmentSet(
    ds: DsServiceJobFile,
    file: ConsensusAlignmentServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleContigSet(ds: DsServiceJobFile,
                               file: ContigServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleReferenceSet(ds: DsServiceJobFile,
                                  file: ReferenceServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleGmapReferenceSet(ds: DsServiceJobFile,
                                      file: GmapReferenceServiceDataSet)
    extends ImportAbleServiceFile

case class ImportAbleTranscriptSet(ds: DsServiceJobFile,
                                   file: TranscriptServiceDataSet)
    extends ImportAbleServiceFile

// Project models

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
      .getOrElse(throw new IllegalArgumentException(
        s"Unknown project state $s, acceptable values are $CREATED, $ACTIVE"))
  }
}

object ProjectUserRole {
  sealed abstract class ProjectUserRole(private val ordinal: Int)
      extends Ordered[ProjectUserRole] {
    override def compare(that: ProjectUserRole) = ordinal.compare(that.ordinal)
  }
  case object OWNER extends ProjectUserRole(ordinal = 3)
  case object CAN_EDIT extends ProjectUserRole(ordinal = 2)
  case object CAN_VIEW extends ProjectUserRole(ordinal = 1)

  def fromString(r: String): ProjectUserRole = {
    Seq(OWNER, CAN_EDIT, CAN_VIEW)
      .find(_.toString == r)
      .getOrElse(throw new IllegalArgumentException(
        s"Unknown project user role $r, acceptable values are $OWNER, $CAN_EDIT, $CAN_VIEW"))
  }
}

// We have a simpler (cheaper to query) project case class for the API
// response that lists many projects,
case class Project(id: Int,
                   name: String,
                   description: String,
                   state: ProjectState.ProjectState,
                   createdAt: JodaDateTime,
                   updatedAt: JodaDateTime,
                   // isActive: false if the project has been deleted, true otherwise
                   isActive: Boolean,
                   grantRoleToAll: Option[ProjectUserRole.ProjectUserRole] =
                     None) {

  def makeFull(datasets: Seq[DataSetMetaDataSet],
               members: Seq[ProjectRequestUser]): FullProject =
    FullProject(id,
                name,
                description,
                state,
                createdAt,
                updatedAt,
                isActive,
                grantRoleToAll,
                datasets,
                members)
}

// and a more detailed case class for the API responses involving
// individual projects.
case class FullProject(id: Int,
                       name: String,
                       description: String,
                       state: ProjectState.ProjectState,
                       createdAt: JodaDateTime,
                       updatedAt: JodaDateTime,
                       isActive: Boolean,
                       grantRoleToAll: Option[ProjectUserRole.ProjectUserRole],
                       datasets: Seq[DataSetMetaDataSet],
                       members: Seq[ProjectRequestUser]) {

  def asRequest: ProjectRequest =
    ProjectRequest(
      name,
      description,
      Some(state),
      Some(ProjectRequestRole.fromProjectUserRole(grantRoleToAll)),
      Some(datasets.map(ds => RequestId(ds.id))),
      Some(members.map(u => ProjectRequestUser(u.login, u.role)))
    )
}

object ProjectRequestRole {
  sealed abstract class ProjectRequestRole(
      val role: Option[ProjectUserRole.ProjectUserRole])
  case object CAN_EDIT
      extends ProjectRequestRole(role = Some(ProjectUserRole.CAN_EDIT))
  case object CAN_VIEW
      extends ProjectRequestRole(role = Some(ProjectUserRole.CAN_VIEW))
  case object NONE extends ProjectRequestRole(role = None)

  private val ALL = Seq(CAN_EDIT, CAN_VIEW, NONE)

  def fromProjectUserRole(
      r: Option[ProjectUserRole.ProjectUserRole]): ProjectRequestRole =
    ALL
      .find(_.role == r)
      .getOrElse(throw new IllegalArgumentException(
        s"No corresponding ProjectRequestRole for ProjectUserRole $r"))

  def fromString(r: String): ProjectRequestRole =
    ALL
      .find(_.toString == r)
      .getOrElse(throw new IllegalArgumentException(
        s"Unknown project request role $r, acceptable values are $CAN_EDIT, $CAN_VIEW, $NONE"))
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
    grantRoleToAll: Option[ProjectRequestRole.ProjectRequestRole],
    datasets: Option[Seq[RequestId]],
    members: Option[Seq[ProjectRequestUser]]) {

  // this returns a copy!
  def appendDataSet(dsId: Int): ProjectRequest = {
    val allDatasets = datasets
      .map(ds => ds ++ Seq(RequestId(dsId)))
      .getOrElse(Seq(RequestId(dsId)))
    this.copy(datasets = Some(allDatasets))
  }
}

case class RequestId(id: Int)

case class ProjectRequestUser(login: String,
                              role: ProjectUserRole.ProjectUserRole)
case class ProjectUser(projectId: Int,
                       login: String,
                       role: ProjectUserRole.ProjectUserRole)

case class UserProjectResponse(role: ProjectUserRole.ProjectUserRole,
                               project: Project)

case class ProjectDatasetResponse(project: Project,
                                  dataset: DataSetMetaDataSet,
                                  role: ProjectUserRole.ProjectUserRole)

case class EulaRecord(user: String,
                      acceptedAt: JodaDateTime,
                      smrtlinkVersion: String,
                      osVersion: String,
                      enableInstallMetrics: Boolean,
                      enableJobMetrics: Boolean)

// Making this backward compatible, but this should be removed
case class EulaAcceptance(user: String,
                          enableInstallMetrics: Boolean,
                          enableJobMetrics: Option[Boolean])

case class DataSetUpdateRequest(isActive: Option[Boolean] = None,
                                bioSampleName: Option[String] = None,
                                wellSampleName: Option[String] = None)
case class DataStoreFileUpdateRequest(isActive: Boolean,
                                      path: Option[String] = None,
                                      fileSize: Option[Long] = None)

// Bundle Related Models

/**
  * PacBio Data Bundle. A general contain for config files, or data files.
  *
  * @param typeId     identifier for the bundle type
  * @param version    version of the bundle (Should use Semver format, 1.2.3.12334 is supported)
  * @param importedAt When the data bundle was imported
  * @param createdBy  User that created (not imported) the bundle
  * @param isActive   If the bundle is active (Only a single bundle type should be active at a given time)
  */
case class PacBioDataBundle(typeId: String,
                            version: String,
                            importedAt: JodaDateTime,
                            createdBy: Option[String],
                            isActive: Boolean = false,
                            description: Option[String] = None) {
  // This is bad OO to duplicate data (version and semVersion)
  // However, this is used to sort bundles and is
  // exposed publicly, but we don't want this to leak to serialization layers (e.g., jsonFormat).
  // Given that case classes are immutable .copy(version="1.2.3") will work
  // as expected. This seems like a reasonable
  // trade off.
  val semVersion = SemVersion.parseWithSlop(version)
}

object PacBioDataBundle {
  implicit val orderBySemVer = SemVersion.orderBySemVersion
  val orderByBundleVersion = Ordering.by((a: PacBioDataBundle) => a.semVersion)
}

/**
  *
  * This is to keep the object model and the IO layer separate.
  *
  * @param tarGzPath Path to the tgz or *.tar.gz data bundle
  * @param path Path to the unzip, untarred Data Bundle
  * @param bundle MetaData About the bundle
  */
case class PacBioDataBundleIO(tarGzPath: Path,
                              path: Path,
                              bundle: PacBioDataBundle)

case class PacBioDataBundleUpgrade(bundle: Option[PacBioDataBundle])

// Use to create a bundle record. All Metadata will be extracted from
// the bundle metadata after it's been extracted
case class PacBioBundleRecord(url: URL)

//MK This is duplicated concept
case class ExternalServerStatus(msg: String, status: String)

// SmrtLink Events/Messages

/**
  * General SmrtLink Event data model
  *
  * @param eventTypeId event Type id. This must be globally unique and map to schema defined in the message (as Json)
  * @param eventTypeVersion Version of the eventTypeId. A tuple of (eventTypeId, eventTypeVersion) must be resolvable to schema defined in *message*
  * @param uuid        globally unique identifier for event message. Assigned by the creator
  * @param createdAt   when the message/event was created at
  * @param message     Json of the message
  */
case class SmrtLinkEvent(eventTypeId: String,
                         eventTypeVersion: Int = 1,
                         uuid: UUID,
                         createdAt: JodaDateTime,
                         message: JsObject)

object EventTypes {
  //  eventTypeId These need to be prefixed with "sl" for downstream analysis via ElasticSearch
  val INST_UPGRADE_NOTIFICATION = "sl_inst_upgrade_notification"
  val SERVER_STARTUP = "sl_smrt_server_startup"
  val IMPORT_BUNDLE = "sl_ts_import_bundle"
  val CHEMISTRY_ACTIVATE_BUNDLE = "sl_chemistry_activate_bundle"
  val JOB_METRICS = "sl_job_metrics"
  // A Test Event. This should be used by any testing related code.
  val TEST = "sl_test_event"
}

case class SmrtLinkSystemEvent(smrtLinkId: UUID,
                               eventTypeId: String,
                               eventTypeVersion: Int = 1,
                               uuid: UUID,
                               createdAt: JodaDateTime,
                               message: JsObject,
                               dnsName: Option[String])

// Request to create a System Status bundle
case class TechSupportSystemStatusRecord(name: String, comment: String)

// Request to create a Job (any job type is supported) bundle
case class TechSupportJobRecord(name: String, comment: String, jobId: Int)

// POST creation of a job event
case class JobEventRecord(state: String, message: String)

case class ReportViewRule(id: String, rules: JsObject)

case class DataSetDeleteServiceOptions(datasetType: String,
                                       ids: Seq[Int],
                                       removeFiles: Boolean = true)

case class TsJobBundleJobServiceOptions(jobId: Int,
                                        user: String,
                                        comment: String)

case class TsSystemStatusServiceOptions(user: String, comment: String)

case class DbBackUpServiceJobOptions(user: String, comment: String)

// Multi Job Options

// Because this is a deferred entity, this must only be a UUID, not IdAble
case class DeferredEntryPoint(fileTypeId: String, uuid: UUID, entryId: String)

// This is explicitly encoded to enable the client
// to specify different Task Options, job name, an so on. for each
// Job that is created
// Note that each list of entry points provided must be consistent with
// the pipeline template provided by pipelineId, otherwise the job
// will fail at runtime
case class DeferredJob(entryPoints: Seq[DeferredEntryPoint],
                       pipelineId: String,
                       taskOptions: Seq[ServiceTaskOptionBase],
                       workflowOptions: Seq[ServiceTaskOptionBase],
                       name: Option[String],
                       description: Option[String],
                       projectId: Option[Int])

// If this model/schema changes, the event type schema version needs
// to be incremented
case class EngineJobMetrics(id: Int,
                            uuid: UUID,
                            createdAt: JodaDateTime,
                            updatedAt: JodaDateTime,
                            state: AnalysisJobStates.JobStates,
                            jobTypeId: String,
                            pipelineId: Option[String],
                            smrtlinkVersion: Option[String],
                            movieIds: Set[String],
                            isActive: Boolean = true,
                            isMultiJob: Boolean = false,
                            importedAt: Option[JodaDateTime] = None)
