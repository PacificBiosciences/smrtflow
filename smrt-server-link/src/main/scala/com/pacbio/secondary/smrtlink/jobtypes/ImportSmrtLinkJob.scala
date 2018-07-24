package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.common.models.CommonModels.{IdAble, UUIDIdAble}

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.Try
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobImportUtils,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJobUtils
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.{
  DataSetMetaDataSet,
  EngineJobEntryPoint
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  ResourceNotFoundError,
  UnprocessableEntityError
}

trait ImportServiceUtils {
  import com.pacbio.common.models.CommonModelImplicits._

  protected def getUuid(id: UUID, mockJobId: Boolean): UUID =
    if (mockJobId) UUID.randomUUID() else id

  def canImportNewJob(jobId: IdAble, dao: JobsDao): Future[String] = {
    val errorMsg = s"Job ${jobId.toIdString} is already present in SMRT Link"
    val successMsg =
      s"Job ${jobId.toIdString} has NOT be imported yet. Job ${jobId.toIdString} can be imported in SMRT Link."

    val f1 = dao
      .getJobById(jobId)
      .flatMap(_ => Future.failed(UnprocessableEntityError(errorMsg)))

    f1.recoverWith {
      case _: ResourceNotFoundError => Future.successful(successMsg)
    }
  }

}

object ImportUtils extends ImportServiceUtils with JobImportUtils {}

case class ImportSmrtLinkJobOptions(
    zipPath: Path,
    mockJobId: Option[Boolean] = None,
    description: Option[String] = None,
    name: Option[String] = None,
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID),
    submit: Option[Boolean] = Some(JobConstants.SUBMIT_DEFAULT_CORE_JOB),
    tags: Option[String] = None)
    extends ServiceJobOptions
    with ValidateJobUtils
    with ImportServiceUtils {

  override def jobTypeId = JobTypeIds.IMPORT_JOB
  override def toJob() = new ImportSmrtLinkJob(this)

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    if (!zipPath.toFile.exists) {
      Some(
        InvalidJobOptionError(s"The file ${zipPath.toString} does not exist"))
    } else {
      // verify that we can extract the manifest, and the job UUID has not
      // already been imported into the system.
      Try {
        val manifest = ImportUtils.getManifest(zipPath)
        val uniqueId = getUuid(manifest.job.uuid, mockJobId.getOrElse(false))
        Await.result(canImportNewJob(UUIDIdAble(uniqueId), dao),
                     DEFAULT_TIMEOUT)
      }.failed.toOption.map(e => InvalidJobOptionError(e.getMessage))
    }
  }
}

class ImportSmrtLinkJob(opts: ImportSmrtLinkJobOptions)
    extends ServiceCoreJob(opts)
    with CoreJobUtils
    with JobImportUtils
    with DataSetFileUtils
    with ImportServiceUtils
    with timeUtils {

  type Out = PacBioDataStore

  import com.pacbio.common.models.CommonModelImplicits._

  private def getEntryPointDataStoreFiles(
      jobId: UUID,
      importPath: Path,
      entryPoints: Seq[BoundEntryPoint]): Seq[DataStoreJobFile] = {
    entryPoints.map { e =>
      e.copy(path = importPath.resolve(e.path))
    } map { e =>
      val now = JodaDateTime.now()
      val md = getDataSetMiniMeta(e.path)
      val f = DataStoreFile(
        uniqueId = md.uuid, // XXX does this need to be mockable too?
        sourceId = "import-job",
        fileTypeId = md.metatype.fileType.fileTypeId,
        fileSize = 0L,
        createdAt = now,
        modifiedAt = now,
        path = e.path.toString,
        name = s"Entry point ${e.entryId}",
        description = s"Imported entry point ${e.entryId}"
      )
      DataStoreJobFile(jobId, f)
    }
  }

  private def getOrImportEntryPoint(
      dao: JobsDao,
      entryPointFile: DataStoreJobFile): Future[DataSetMetaDataSet] = {
    val uuid = entryPointFile.dataStoreFile.uniqueId

    def andLog(ds: DataSetMetaDataSet): Future[DataSetMetaDataSet] = Future {
      // This should be to the import Job writer
      logger.info(
        s"Entry point dataset ${uuid.toString} already present in database. Skipping file.")
      ds
    }

    def importNewEntry(f: DataStoreJobFile): Future[DataSetMetaDataSet] =
      for {
        _ <- dao.addDataStoreFile(entryPointFile)
        ds <- dao.getDataSetMetaData(uuid)
      } yield ds

    dao
      .getDataSetMetaData(uuid)
      .flatMap(andLog)
      .recoverWith {
        case _: ResourceNotFoundError => importNewEntry(entryPointFile)
      }
  }

  private def getOrImportEntryPoints(dao: JobsDao,
                                     entryPointFiles: Seq[DataStoreJobFile])
    : Future[Seq[DataSetMetaDataSet]] =
    Future.sequence {
      entryPointFiles.map { f =>
        getOrImportEntryPoint(dao, f)
      }
    }

  private def toDataStoreJobFile(ds: DataStoreFile,
                                 importedJob: UUID,
                                 importedJobPath: Path): DataStoreJobFile = {
    // this also may have the UUID mocked for testing
    val path = importedJobPath.resolve(ds.path.toString).toString
    val uniqueId = getUuid(ds.uniqueId, opts.mockJobId.getOrElse(false))
    DataStoreJobFile(importedJob, ds.copy(path = path, uniqueId = uniqueId))
  }

  private def addImportedJobFiles(
      dao: JobsDao,
      importedJob: EngineJob,
      datastoreFiles: Seq[DataStoreFile]): Future[Seq[MessageResponse]] = {
    Future.sequence {
      datastoreFiles
        .filter(!_.isChunked)
        .map(f =>
          toDataStoreJobFile(f, importedJob.uuid, Paths.get(importedJob.path)))
        .map { f =>
          dao.addDataStoreFile(f)
        }
    }
  }

  private def addEntryPoints(dao: JobsDao,
                             jobId: Int, // unzipped job
                             epDsFiles: Seq[DataStoreJobFile]) = {
    Future.sequence {
      epDsFiles.map { f =>
        dao.insertEntryPoint(
          EngineJobEntryPoint(jobId,
                              f.dataStoreFile.uniqueId,
                              f.dataStoreFile.fileTypeId))
      }
    }
  }
  private def toManifestDataStoreFile(path: Path): DataStoreFile =
    DataStoreFile(
      uniqueId = UUID.randomUUID(),
      sourceId = "import-job",
      fileTypeId = FileTypes.JSON.fileTypeId,
      fileSize = path.toFile.length(),
      createdAt = JodaDateTime.now(),
      modifiedAt = JodaDateTime.now(),
      path = path.toString,
      name = "Imported job manifest",
      description = "Imported job manifest"
    )

  private def expandJobF(zipPath: Path,
                         importedPath: Path): Future[JobImportSummary] =
    Future {
      blocking { Future.fromTry { expandJob(zipPath, importedPath) } }
    }.flatMap(identity)

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val startedAt = JodaDateTime.now()

    def writeFilesToDataStore(files: Seq[DataStoreFile]): PacBioDataStore = {
      val endedAt = JodaDateTime.now()
      val ds = PacBioDataStore(startedAt, endedAt, "0.1.0", files)
      val datastoreJson =
        resources.path.resolve(JobConstants.OUTPUT_DATASTORE_JSON)
      writeDataStore(ds, datastoreJson)
      resultsWriter.write(
        s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}")
      ds
    }

    // for testing we need to be able to swap in a new UUID
    def getU(uuid: UUID): UUID = getUuid(uuid, opts.mockJobId.getOrElse(false))

    val logFile = getStdOutLog(resources, dao)
    val manifest = getManifest(opts.zipPath)

    val jobDsFiles =
      manifest.datastore.map(_.files).getOrElse(Seq.empty[DataStoreFile])

    val fx2 = for {
      thisJob <- dao.getJobById(resources.jobId)
      exportedJob <- Future.successful(
        manifest.job.copy(uuid = getU(manifest.job.uuid),
                          projectId = thisJob.projectId))
      importedJob <- dao.importRawEngineJob(exportedJob)
      importedPath <- Future.successful(Paths.get(importedJob.path))
      epDsFiles <- Future.successful(
        getEntryPointDataStoreFiles(resources.jobId,
                                    importedPath,
                                    manifest.entryPoints))
      jobSummary <- expandJobF(opts.zipPath, importedPath)
      entryPointDatasets <- getOrImportEntryPoints(dao, epDsFiles)
      _ <- addEntryPoints(dao, importedJob.id, epDsFiles)
      _ <- addImportedJobFiles(dao, importedJob, jobDsFiles)
      jobDsManifestJson <- Future.successful(
        toManifestDataStoreFile(
          importedPath.resolve("export-job-manifest.json")))
      ds <- Future.successful(
        writeFilesToDataStore(Seq(logFile, jobDsManifestJson)))
    } yield ds

    convertTry(runAndBlock(fx2, 30.seconds),
               resultsWriter,
               startedAt,
               resources.jobId)
  }
}
