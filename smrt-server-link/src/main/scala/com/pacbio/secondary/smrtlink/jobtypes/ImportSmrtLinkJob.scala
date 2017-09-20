package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.collection.mutable.ArrayBuffer
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.{Try, Failure, Success}

import org.joda.time.{DateTime => JodaDateTime}
import org.joda.time.format.DateTimeFormat

import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.JobsDao
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
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.EngineJobEntryPoint
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError

case class ImportSmrtLinkJobOptions(zipPath: Path,
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

  private def resolveJobs(dao: JobsDao,
                          jobIds: Seq[IdAble]): Future[Seq[EngineJob]] =
    Future.sequence(jobIds.map(dao.getJobById(_)))

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    val startedAt = JodaDateTime.now()
    val manifest = getManifest(opts.zipPath)

    val fx1 = for {
      job <- dao.getJobById(resources.jobId)
      imported <- dao.importRawEngineJob(manifest.job, job)
    } yield (job, imported)
    val (job, imported) = Await.result(fx1, 30.seconds)
    val importPath = Paths.get(imported.path)
    val summary = expandJob(opts.zipPath, importPath)

    val epDsFiles: Seq[DataStoreJobFile] = manifest.entryPoints.map { e =>
      e.copy(path = importPath.resolve(e.path).toString)
    } map { e =>
      val now = JodaDateTime.now()
      val md = getDataSetMiniMeta(Paths.get(e.path))
      val f = DataStoreFile(
        uniqueId = md.uuid,
        sourceId = "import-job",
        fileTypeId = md.metatype.fileType.fileTypeId,
        fileSize = 0L,
        createdAt = now,
        modifiedAt = now,
        path = e.path,
        name = s"Entry point ${e.entryId}",
        description = s"Imported entry point ${e.entryId}"
      )
      DataStoreJobFile(job.uuid, f)
    }

    val fx2 = for {
      // add datastore files for imported job
      _ <- Future.sequence {
        manifest.datastore
          .map { ds =>
            ds.files.map { f =>
              DataStoreJobFile(
                imported.uuid,
                f.copy(path = importPath.resolve(f.path.toString).toString))
            }
          }
          .getOrElse(Seq.empty[DataStoreJobFile])
          .map { f =>
            dao.addDataStoreFile(f)
          }
      }
      // import entry points as part of the import-job job
      _ <- Future.sequence(epDsFiles.map(f => dao.addDataStoreFile(f)))
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

    val dsFiles = epDsFiles.map(_.dataStoreFile) ++ Seq(
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
