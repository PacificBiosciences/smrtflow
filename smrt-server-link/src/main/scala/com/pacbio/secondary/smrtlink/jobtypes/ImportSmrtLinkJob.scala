package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.Try

import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultsWriter,
  JobImportUtils,
  AnalysisJobStates
}
import com.pacbio.secondary.smrtlink.analysis.jobs.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.{
  DataSetMetaDataSet,
  EngineJobEntryPoint
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError

trait ImportServiceUtils {
  import com.pacbio.common.models.CommonModelImplicits._

  protected def getOptionalRecord[T](fx: () => Future[T]): Future[Option[T]] =
    fx().map(x => Some(x)).recover { case e: Exception => None }

  protected def getUuid(id: UUID, mockJobId: Boolean) =
    if (mockJobId) UUID.randomUUID() else id

  protected def canImportJob(jobId: UUID, dao: JobsDao): Future[String] = {
    getOptionalRecord(() => dao.getJobById(jobId))
      .flatMap { jobOpt =>
        jobOpt
          .map { job =>
            Future.failed(new UnprocessableEntityError(
              s"Job ${jobId.toString} is already present in the database"))
          }
          .getOrElse {
            Future.successful(s"Job ${jobId.toString} not found.")
          }
      }
  }
}

object ImportUtils extends ImportServiceUtils with JobImportUtils {}

case class ImportSmrtLinkJobOptions(zipPath: Path,
                                    mockJobId: Option[Boolean] = None,
                                    description: Option[String] = None,
                                    name: Option[String] = None,
                                    projectId: Option[Int] = Some(
                                      JobConstants.GENERAL_PROJECT_ID))
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
      // verify that we can extract the manifest, and the job UUID is unique
      Try {
        val manifest = ImportUtils.getManifest(zipPath)
        val uniqueId = getUuid(manifest.job.uuid, mockJobId.getOrElse(false))
        Await.result(canImportJob(uniqueId, dao), DEFAULT_TIMEOUT)
      }.failed.toOption.map(e => InvalidJobOptionError(e.getMessage))
    }
  }
}

class ImportSmrtLinkJob(opts: ImportSmrtLinkJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils
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

  /**
    * get dataset metadata for each entry point, importing any unknown datasets
    * as part of the import-job job
    */
  private def getOrImportEntryPoint(
      dao: JobsDao,
      jobId: Int, // import-job job
      entryPointFile: DataStoreJobFile): Future[DataSetMetaDataSet] = {
    val uuid = entryPointFile.dataStoreFile.uniqueId
    getOptionalRecord { () =>
      dao.getDataSetMetaData(uuid)
    }.flatMap { dsOpt =>
      dsOpt
        .map { ds =>
          logger.info(
            s"Entry point dataset ${uuid.toString} already present in database")
          Future.successful(ds)
        }
        .getOrElse {
          logger.info(s"Adding entry point dataset ${uuid.toString}")
          dao.addDataStoreFile(entryPointFile).flatMap { _ =>
            dao.getDataSetMetaData(uuid)
          }
        }
    }
  }

  private def getOrImportEntryPoints(dao: JobsDao,
                                     jobId: Int, // import-job job
                                     entryPointFiles: Seq[DataStoreJobFile]) =
    Future.sequence {
      entryPointFiles.map { f =>
        getOrImportEntryPoint(dao, jobId, f)
      }
    }

  private def addImportedJobFiles(dao: JobsDao,
                                  importedJob: EngineJob,
                                  datastoreFiles: Seq[DataStoreFile]) = {
    Future.sequence {
      datastoreFiles
        .map { f =>
          // this also may have the UUID mocked for testing
          val path = Paths
            .get(importedJob.path)
            .resolve(f.path.toString)
            .toString
          val uniqueId = getUuid(f.uniqueId, opts.mockJobId.getOrElse(false))
          DataStoreJobFile(importedJob.uuid,
                           f.copy(path = path, uniqueId = uniqueId))
        }
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

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    val startedAt = JodaDateTime.now()
    val manifest = getManifest(opts.zipPath)
    // for testing we need to be able to swap in a new UUID
    val exportedJob = manifest.job.copy(
      uuid = getUuid(manifest.job.uuid, opts.mockJobId.getOrElse(false)))

    val fx1 = for {
      job <- dao.getJobById(resources.jobId)
      imported <- dao.importRawEngineJob(exportedJob, job)
    } yield (job, imported)
    val (job, imported) = Await.result(fx1, 30.seconds)
    val importPath = Paths.get(imported.path)
    val summary = expandJob(opts.zipPath, importPath)
    val jobDsFiles =
      manifest.datastore.map(_.files).getOrElse(Seq.empty[DataStoreFile])
    val epDsFiles =
      getEntryPointDataStoreFiles(job.uuid, importPath, manifest.entryPoints)

    val fx2 = for {
      entryPointDatasets <- getOrImportEntryPoints(dao, job.id, epDsFiles)
      _ <- addEntryPoints(dao, imported.id, epDsFiles)
      _ <- addImportedJobFiles(dao, imported, jobDsFiles)
    } yield entryPointDatasets
    val entryPointDatasets = Await.result(fx2, 30.seconds)

    val dsFiles = epDsFiles
      .zip(entryPointDatasets)
      .filter(_._2.jobId == job.id)
      .map(_._1.dataStoreFile) ++ Seq(
      DataStoreFile(
        uniqueId = UUID.randomUUID(),
        sourceId = "import-job",
        fileTypeId = FileTypes.JSON.fileTypeId,
        fileSize = 0L,
        createdAt = JodaDateTime.now(),
        modifiedAt = JodaDateTime.now(),
        path = importPath.resolve("export-job-manifest.json").toString,
        name = "Imported job manifest",
        description = "Imported job manifest"
      ))
    val endedAt = JodaDateTime.now()
    val ds = PacBioDataStore(startedAt, endedAt, "0.1.0", dsFiles)
    val datastoreJson = resources.path.resolve("datastore.json")
    writeDataStore(ds, datastoreJson)
    resultsWriter.write(
      s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}")
    Right(ds)
  }
}
