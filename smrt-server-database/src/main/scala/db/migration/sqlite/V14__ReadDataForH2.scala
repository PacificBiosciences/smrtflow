package db.migration.sqlite

import java.util.UUID

import db.migration.SlickMigration
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.driver.SQLiteDriver.api._
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future


class V14__ReadDataForH2 extends JdbcMigration with SlickMigration {
  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    val action = for {
      je   <- V14Schema.jobEvents.result
      jt   <- V14Schema.jobTags.result
      jst  <- V14Schema.jobsTags.result
      ej   <- V14Schema.engineJobs.result
      jr   <- V14Schema.jobResults.result
      u    <- V14Schema.users.result
      p    <- V14Schema.projects.result
      pu   <- V14Schema.projectsUsers.result
      dst  <- V14Schema.datasetTypes.result
      ejds <- V14Schema.engineJobsDataSets.result
      dsm  <- V14Schema.dsMetaData2.result
      sds  <- V14Schema.dsSubread2.result
      hds  <- V14Schema.dsHdfSubread2.result
      rds  <- V14Schema.dsReference2.result
      gds  <- V14Schema.dsGmapReference2.result
      ads  <- V14Schema.dsAlignment2.result
      bds  <- V14Schema.dsBarcode2.result
      cds  <- V14Schema.dsCCSread2.result
      cads <- V14Schema.dsCCSAlignment2.result
      cods <- V14Schema.dsContig2.result
      dsf  <- V14Schema.datastoreServiceFiles.result
      rs   <- V14Schema.runSummaries.result
      dm   <- V14Schema.dataModels.result
      cm   <- V14Schema.collectionMetadata.result
      s    <- V14Schema.samples.result
    } yield new V14Data(je, jt, jst, ej, jr, u, p, pu, dst, ejds, dsm, sds, hds, rds, gds, ads, bds, cds, cads, cods, dsf, rs, dm, cm, s)

    db.run(action).map(d => V14Data.data = Some(d))
  }
}

object V14Data {
  var data: Option[V14Data] = None
}

case class V14Data(
  jobEvents: Seq[(UUID, Int, String, String, Long)],
  jobTags: Seq[(Int, String)],
  jobsTags: Seq[(Int, Int)],
  engineJobs: Seq[(Int, UUID, String, String, Long, Long, String, String, String, String, Option[String])],
  jobResults: Seq[(Int, String)],
  users: Seq[(Int, String, String, Long, Long)],
  projects: Seq[(Int, String, String, String, Long, Long)],
  projectsUsers: Seq[(Int, String, String)],
  datasetTypes: Seq[(String, String, String, Long, Long, String)],
  engineJobsDataSets: Seq[(Int, UUID, String)],
  dsMetaData2: Seq[(Int, UUID, String, String, Long, Long, Long, Long, String, String, String, String, Int, Int, Int, Boolean)],
  dsSubread2: Seq[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)],
  dsHdfSubread2: Seq[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)],
  dsReference2: Seq[(Int, UUID, String, String)],
  dsGmapReference2: Seq[(Int, UUID, String, String)],
  dsAlignment2: Seq[(Int, UUID)],
  dsBarcode2: Seq[(Int, UUID)],
  dsCCSread2: Seq[(Int, UUID)],
  dsCCSAlignment2: Seq[(Int, UUID)],
  dsContig2: Seq[(Int, UUID)],
  datastoreServiceFiles: Seq[(UUID, String, String, Long, Long, Long, Long, String, Int, UUID, String, String)],
  runSummaries: Seq[(UUID, String, Option[String], Option[String], Option[Long], Option[Long], Option[Long], String, Int, Int, Int, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Boolean)],
  dataModels: Seq[(String, UUID)],
  collectionMetadata: Seq[(UUID, UUID, String, String, Option[String], Option[String], Option[String], String, Option[String], Option[String], Double, Option[Long], Option[Long], Option[String])],
  samples: Seq[(String, UUID, String, String, Long)])

object V14Schema {
  class JobEventsT(tag: Tag) extends Table[(UUID, Int, String, String, Long)](tag, "job_events") {
    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)
    def state: Rep[String] = column[String]("state")
    def jobId: Rep[Int] = column[Int]("job_id")
    def message: Rep[String] = column[String]("message")
    def createdAt: Rep[Long] = column[Long]("created_at")
    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)
    def * : ProvenShape[(UUID, Int, String, String, Long)] = (id, jobId, state, message, createdAt)
  }

  class JobTags(tag: Tag) extends Table[(Int, String)](tag, "job_tags") {
    def id: Rep[Int] = column[Int]("job_tag_id", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("name")
    def * : ProvenShape[(Int, String)] = (id, name)
  }

  class JobsTags(tag: Tag) extends Table[(Int, Int)](tag, "jobs_tags") {
    def jobId: Rep[Int] = column[Int]("job_id")
    def tagId: Rep[Int] = column[Int]("job_tag_id")
    def * : ProvenShape[(Int, Int)] = (jobId, tagId)
    def jobTagFK = foreignKey("job_tag_fk", tagId, jobTags)(a => a.id)
    def jobFK = foreignKey("job_fk", jobId, engineJobs)(b => b.id)
  }

  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, Long, Long, String, String, String, String, Option[String])](tag, "engine_jobs") {
    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)
    def uuid: Rep[UUID] = column[UUID]("uuid")
    def pipelineId: Rep[String] = column[String]("pipeline_id")
    def name: Rep[String] = column[String]("name")
    def state: Rep[String] = column[String]("state")
    def createdAt: Rep[Long] = column[Long]("created_at")
    def updatedAt: Rep[Long] = column[Long]("updated_at")
    def jobTypeId: Rep[String] = column[String]("job_type_id")
    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))
    def jsonSettings: Rep[String] = column[String]("json_settings")
    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")
    def * : ProvenShape[(Int, UUID, String, String, Long, Long, String, String, String, String, Option[String])] = (id, uuid, name, pipelineId, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy)
  }

  class JobResultT(tag: Tag) extends Table[(Int, String)](tag, "job_results") {
    def id: Rep[Int] = column[Int]("job_result_id")
    def host: Rep[String] = column[String]("host_name")
    def jobId: Rep[Int] = column[Int]("job_id")
    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)
    def * : ProvenShape[(Int, String)] = (id, host)
  }

  class UsersT(tag: Tag) extends Table[(Int, String, String, Long, Long)](tag, "users") {
    def id: Rep[Int] = column[Int]("user_id", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("name")
    def token: Rep[String] = column[String]("token")
    def createdAt: Rep[Long] = column[Long]("created_at")
    def updatedAt: Rep[Long] = column[Long]("updated_at")
    def * : ProvenShape[(Int, String, String, Long, Long)] = (id, name, token, createdAt, updatedAt)
  }

  class ProjectsT(tag: Tag) extends Table[(Int, String, String, String, Long, Long)](tag, "projects") {
    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("name")
    def description: Rep[String] = column[String]("description")
    def state: Rep[String] = column[String]("state")
    def createdAt: Rep[Long] = column[Long]("created_at")
    def updatedAt: Rep[Long] = column[Long]("updated_at")
    def * : ProvenShape[(Int, String, String, String, Long, Long)] = (id, name, description, state, createdAt, updatedAt)
  }

  class ProjectsUsersT(tag: Tag) extends Table[(Int, String, String)](tag, "projects_users") {
    def projectId: Rep[Int] = column[Int]("project_id")
    def login: Rep[String] = column[String]("login")
    def role: Rep[String] = column[String]("role")
    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)
    def * : ProvenShape[(Int, String, String)] = (projectId, login, role)
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class DataSetTypesT(tag: Tag) extends Table[(String, String, String, Long, Long, String)](tag, "dataset_types") {
    def id: Rep[String] = column[String]("dataset_type_id", O.PrimaryKey)
    def idx = index("index_id", id, unique = true)
    def name: Rep[String] = column[String]("name")
    def description: Rep[String] = column[String]("description")
    def createdAt: Rep[Long] = column[Long]("created_at")
    def updatedAt: Rep[Long] = column[Long]("updated_at")
    def shortName: Rep[String] = column[String]("short_name")
    def * : ProvenShape[(String, String, String, Long, Long, String)] = (id, name, description, createdAt, updatedAt, shortName)
  }

  class EngineJobDataSetT(tag: Tag) extends Table[(Int, UUID, String)](tag, "engine_jobs_datasets") {
    def jobId: Rep[Int] = column[Int]("job_id")
    def datasetUUID: Rep[UUID] = column[UUID]("dataset_uuid")
    def datasetType: Rep[String] = column[String]("dataset_type")
    def * : ProvenShape[(Int, UUID, String)] = (jobId, datasetUUID, datasetType)
  }

  class DataSetMetaT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, Long, Long, Long, Long, String, String, String, String, Int, Int, Int, Boolean)](tag, "dataset_metadata") {
    def name: Rep[String] = column[String]("name")
    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))
    def createdAt: Rep[Long] = column[Long]("created_at")
    def updatedAt: Rep[Long] = column[Long]("updated_at")
    def numRecords: Rep[Long] = column[Long]("num_records")
    def totalLength: Rep[Long] = column[Long]("total_length")
    def tags: Rep[String] = column[String]("tags")
    def version: Rep[String] = column[String]("version")
    def comments: Rep[String] = column[String]("comments")
    def md5: Rep[String] = column[String]("md5")
    def userId: Rep[Int] = column[Int]("user_id")
    def jobId: Rep[Int] = column[Int]("job_id")
    def projectId: Rep[Int] = column[Int]("project_id")
    def isActive: Rep[Boolean] = column[Boolean]("is_active")
    def * : ProvenShape[(Int, UUID, String, String, Long, Long, Long, Long, String, String, String, String, Int, Int, Int, Boolean)] = (id, uuid, name, path, createdAt, updatedAt, numRecords, totalLength, tags, version, comments, md5, userId, jobId, projectId, isActive)
  }

  class SubreadDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)](tag, "dataset_subreads") {
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
    def * : ProvenShape[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)] = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion)
  }

  class HdfSubreadDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)](tag, "dataset_hdfsubreads") {
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
    def * : ProvenShape[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)] = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion)
  }

  class ReferenceDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String)](tag, "dataset_references") {
    def ploidy: Rep[String] = column[String]("ploidy")
    def organism: Rep[String] = column[String]("organism")
    def * : ProvenShape[(Int, UUID, String, String)] = (id, uuid, ploidy, organism)
  }

  class GmapReferenceDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String)](tag, "dataset_gmapreferences") {
    def ploidy: Rep[String] = column[String]("ploidy")
    def organism: Rep[String] = column[String]("organism")
    def * : ProvenShape[(Int, UUID, String, String)] = (id, uuid, ploidy, organism)
  }

  class AlignmentDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_alignments") {
    def * : ProvenShape[(Int, UUID)] = (id, uuid)
  }

  class BarcodeDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_barcodes") {
    def * : ProvenShape[(Int, UUID)] = (id, uuid)
  }

  class CCSreadDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_ccsreads") {
    def * : ProvenShape[(Int, UUID)] = (id, uuid)
  }

  class ConsensusAlignmentDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_ccsalignments") {
    def * : ProvenShape[(Int, UUID)] = (id, uuid)
  }

  class ContigDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_contigs") {
    def * : ProvenShape[(Int, UUID)] = (id, uuid)
  }

  class PacBioDataStoreFileT(tag: Tag) extends Table[(UUID, String, String, Long, Long, Long, Long, String, Int, UUID, String, String)](tag, "datastore_files") {
    def uuid: Rep[UUID] = column[UUID]("uuid", O.PrimaryKey)
    def fileTypeId: Rep[String] = column[String]("file_type_id")
    def sourceId: Rep[String] = column[String]("source_id")
    def fileSize: Rep[Long] = column[Long]("file_size")
    def createdAt: Rep[Long] = column[Long]("created_at")
    def modifiedAt: Rep[Long] = column[Long]("modified_at")
    def importedAt: Rep[Long] = column[Long]("imported_at")
    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))
    def jobId: Rep[Int] = column[Int]("job_id")
    def jobUUID: Rep[UUID] = column[UUID]("job_uuid")
    def name: Rep[String] = column[String]("name")
    def description: Rep[String] = column[String]("description")
    def * : ProvenShape[(UUID, String, String, Long, Long, Long, Long, String, Int, UUID, String, String)] = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID, name, description)
  }

  class RunSummariesT(tag: Tag) extends Table[(UUID, String, Option[String], Option[String], Option[Long], Option[Long], Option[Long], String, Int, Int, Int, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Boolean)](tag, "RUN_SUMMARIES") {
    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)
    def name: Rep[String] = column[String]("NAME")
    def summary: Rep[Option[String]] = column[Option[String]]("SUMMARY")
    def createdBy: Rep[Option[String]] = column[Option[String]]("CREATED_BY")
    def createdAt: Rep[Option[Long]] = column[Option[Long]]("CREATED_AT")
    def startedAt: Rep[Option[Long]] = column[Option[Long]]("STARTED_AT")
    def completedAt: Rep[Option[Long]] = column[Option[Long]]("COMPLETED_AT")
    def status: Rep[String] = column[String]("STATUS")
    def totalCells: Rep[Int] = column[Int]("TOTAL_CELLS")
    def numCellsCompleted: Rep[Int] = column[Int]("NUM_CELLS_COMPLETED")
    def numCellsFailed: Rep[Int] = column[Int]("NUM_CELLS_FAILED")
    def instrumentName: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")
    def instrumentSerialNumber: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_SERIAL_NUMBER")
    def instrumentSwVersion: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_SW_VERSION")
    def primaryAnalysisSwVersion: Rep[Option[String]] = column[Option[String]]("PRIMARY_ANALYSIS_SW_VERSION")
    def context: Rep[Option[String]] = column[Option[String]]("CONTEXT")
    def terminationInfo: Rep[Option[String]] = column[Option[String]]("TERMINATION_INFO")
    def reserved: Rep[Boolean] = column[Boolean]("RESERVED")
    def * : ProvenShape[(UUID, String, Option[String], Option[String], Option[Long], Option[Long], Option[Long], String, Int, Int, Int, Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Boolean)] = (
      uniqueId,
      name,
      summary,
      createdBy,
      createdAt,
      startedAt,
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

  class DataModelsT(tag: Tag) extends Table[(String, UUID)](tag, "DATA_MODELS") {
    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)
    def dataModel: Rep[String] = column[String]("DATA_MODEL")
    def * : ProvenShape[(String, UUID)] = (dataModel, uniqueId)
    def summary = foreignKey("SUMMARY_FK", uniqueId, runSummaries)(_.uniqueId)
  }

  class CollectionMetadataT(tag: Tag) extends Table[(UUID, UUID, String, String, Option[String], Option[String], Option[String], String, Option[String], Option[String], Double, Option[Long], Option[Long], Option[String])](tag, "COLLECTION_METADATA") {
    def runId: Rep[UUID] = column[UUID]("RUN_ID")
    def run = foreignKey("RUN_FK", runId, runSummaries)(_.uniqueId)
    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)
    def well: Rep[String] = column[String]("WELL")
    def name: Rep[String] = column[String]("NAME")
    def summary: Rep[Option[String]] = column[Option[String]]("COLUMN")
    def context: Rep[Option[String]] = column[Option[String]]("CONTEXT")
    def collectionPathUri: Rep[Option[String]] = column[Option[String]]("COLLECTION_PATH_URI")
    def status: Rep[String] = column[String]("STATUS")
    def instrumentId: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_ID")
    def instrumentName: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")
    def movieMinutes: Rep[Double] = column[Double]("MOVIE_MINUTES")
    def startedAt: Rep[Option[Long]] = column[Option[Long]]("STARTED_AT")
    def completedAt: Rep[Option[Long]] = column[Option[Long]]("COMPLETED_AT")
    def terminationInfo: Rep[Option[String]] = column[Option[String]]("TERMINATION_INFO")
    def * : ProvenShape[(UUID, UUID, String, String, Option[String], Option[String], Option[String], String, Option[String], Option[String], Double, Option[Long], Option[Long], Option[String])] = (
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
      startedAt,
      completedAt,
      terminationInfo)
  }

  class SampleT(tag: Tag) extends Table[(String, UUID, String, String, Long)](tag, "SAMPLE") {
    def details: Rep[String] = column[String]("DETAILS")
    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)
    def name: Rep[String] = column[String]("NAME")
    def createdBy: Rep[String] = column[String]("CREATED_BY")
    def createdAt: Rep[Long] = column[Long]("CREATED_AT")
    def * : ProvenShape[(String, UUID, String, String, Long)] = (details, uniqueId, name, createdBy, createdAt)
  }

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
  lazy val users = TableQuery[UsersT]
  lazy val projects = TableQuery[ProjectsT]
  lazy val projectsUsers = TableQuery[ProjectsUsersT]
  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val engineJobsDataSets = TableQuery[EngineJobDataSetT]
  lazy val jobEvents = TableQuery[JobEventsT]
  lazy val jobTags = TableQuery[JobTags]
  lazy val jobsTags = TableQuery[JobsTags]
  lazy val jobResults = TableQuery[JobResultT]
  lazy val datasetTypes = TableQuery[DataSetTypesT]
  lazy val runSummaries = TableQuery[RunSummariesT]
  lazy val dataModels = TableQuery[DataModelsT]
  lazy val collectionMetadata = TableQuery[CollectionMetadataT]
  lazy val samples = TableQuery[SampleT]
}
