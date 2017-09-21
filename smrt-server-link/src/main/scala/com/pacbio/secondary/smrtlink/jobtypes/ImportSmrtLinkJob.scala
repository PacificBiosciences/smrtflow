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
  JobResultWriter,
  JobImportUtils,
  AnalysisJobStates
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.{
  DataSetMetaDataSet,
  EngineJobEntryPoint
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError

case class ImportSmrtLinkJobOptions(zipPath: Path,
                                    mockJobId: Option[Boolean] = None,
                                    description: Option[String] = None,
                                    name: Option[String] = None,
                                    projectId: Option[Int] = Some(
                                      JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions
    with ValidateJobUtils {

  override def jobTypeId = JobTypeIds.IMPORT_JOB
  override def toJob() = new ImportSmrtLinkJob(this)

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    if (!zipPath.toFile.exists) {
      Some(
        InvalidJobOptionError(s"The file ${zipPath.toString} does not exist"))
    } else None
  }
}

class ImportSmrtLinkJob(opts: ImportSmrtLinkJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils
    with JobImportUtils
    with DataSetFileUtils
    with timeUtils {

  type Out = PacBioDataStore

  import com.pacbio.common.models.CommonModelImplicits._

  private def getUuid(id: UUID) =
    if (opts.mockJobId.getOrElse(false)) UUID.randomUUID() else id

  private def getEntryPointDataStoreFiles(
      jobId: UUID,
      importPath: Path,
      entryPoints: Seq[BoundEntryPoint]): Seq[DataStoreJobFile] = {
    entryPoints.map { e =>
      e.copy(path = importPath.resolve(e.path).toString)
    } map { e =>
      val now = JodaDateTime.now()
      val md = getDataSetMiniMeta(Paths.get(e.path))
      val f = DataStoreFile(
        uniqueId = md.uuid, // XXX does this need to be mockable too?
        sourceId = "import-job",
        fileTypeId = md.metatype.fileType.fileTypeId,
        fileSize = 0L,
        createdAt = now,
        modifiedAt = now,
        path = e.path,
        name = s"Entry point ${e.entryId}",
        description = s"Imported entry point ${e.entryId}"
      )
      DataStoreJobFile(jobId, f)
    }
  }

  private def getUniqueDataStoreFiles(
      dataSets: Seq[Option[DataSetMetaDataSet]],
      entryPointFiles: Seq[DataStoreJobFile]): Seq[DataStoreJobFile] = {
    val epDsFilesUnique =
      entryPointFiles
        .zip(dataSets)
        .map {
          case (f, d) => if (d.isEmpty) Some(f) else None
        }
        .flatten
    val nPresent = entryPointFiles.size - epDsFilesUnique.size
    logger.info(s"Filtered $nPresent entry points already present in database")
    epDsFilesUnique
  }

  private def addImportedJobFiles(dao: JobsDao,
                                  importedJob: EngineJob,
                                  datastore: Option[PacBioDataStore]) = {
    datastore
      .map { ds =>
        ds.files.map { f =>
          // this also may have the UUID mocked for testing
          DataStoreJobFile(importedJob.uuid,
                           f.copy(path = Paths
                                    .get(importedJob.path)
                                    .resolve(f.path.toString)
                                    .toString,
                                  uniqueId = getUuid(f.uniqueId)))
        }
      }
      .getOrElse(Seq.empty[DataStoreJobFile])
      .map { f =>
        dao.addDataStoreFile(f)
      }
  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    val startedAt = JodaDateTime.now()
    val manifest = getManifest(opts.zipPath)
    // for testing we need to be able to swap in a new UUID
    val exportedJob = manifest.job.copy(uuid = getUuid(manifest.job.uuid))

    val fx1 = for {
      job <- dao.getJobById(resources.jobId)
      imported <- dao.importRawEngineJob(exportedJob, job)
    } yield (job, imported)
    val (job, imported) = Await.result(fx1, 30.seconds)
    val importPath = Paths.get(imported.path)
    val summary = expandJob(opts.zipPath, importPath)
    val epDsFiles =
      getEntryPointDataStoreFiles(job.uuid, importPath, manifest.entryPoints)

    val epExisting: Seq[Option[DataSetMetaDataSet]] = epDsFiles.map { f =>
      Try {
        Await.result(dao.getDataSetMetaData(f.dataStoreFile.uniqueId),
                     30.seconds)
      }.toOption
    }
    val epDsFilesUnique = getUniqueDataStoreFiles(epExisting, epDsFiles)

    val fx2 = for {
      _ <- Future.sequence {
        addImportedJobFiles(dao, imported, manifest.datastore)
      }
      // import entry points as part of the import-job job, but only if they
      // are not already in the database
      _ <- Future.sequence {
        epDsFilesUnique.map { f =>
          dao.addDataStoreFile(f)
        }
      }
      // get metadata for entry points
      datasets <- Future.sequence {
        epDsFiles.map { f =>
          dao.getDataSetMetaData(f.dataStoreFile.uniqueId)
        }
      }
      // finally insert entry points for imported job
      entryPoints <- Future.sequence {
        epDsFiles.zip(datasets).map {
          case (f, d) =>
            dao.insertEntryPoint(
              EngineJobEntryPoint(imported.id,
                                  d.uuid,
                                  f.dataStoreFile.fileTypeId))
        }
      }
    } yield entryPoints
    val eps = Await.result(fx2, 30.seconds)

    val dsFiles = epDsFilesUnique.map(_.dataStoreFile) ++ Seq(
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
