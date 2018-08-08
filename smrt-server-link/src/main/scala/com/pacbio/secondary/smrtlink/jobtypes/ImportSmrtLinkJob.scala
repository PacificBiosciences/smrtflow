package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.common.models.CommonModels.{IdAble, UUIDIdAble}

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._
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
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.{
  BoundServiceEntryPoint,
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
    def toError(job: EngineJob) =
      s"Job Id: ${job.id} (${job.uuid}) is already present in SMRT Link. Unable to import Job."

    val successMsg =
      s"Job ${jobId.toIdString} has NOT be imported yet. Job ${jobId.toIdString} can be imported in SMRT Link."

    val f1 = dao
      .getJobById(jobId)
      .flatMap(job => Future.failed(UnprocessableEntityError(toError(job))))

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
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  private def entryPointFileToDataStoreFile(
      boundEntryPoint: BoundEntryPoint): DataStoreFile = {
    val now = JodaDateTime.now()
    val md = getDataSetMiniMeta(boundEntryPoint.path)

    DataStoreFile(
      uniqueId = md.uuid, // XXX does this need to be mockable too?
      sourceId = "import-job",
      fileTypeId = md.metatype.fileType.fileTypeId,
      fileSize = boundEntryPoint.path.toFile.length(),
      createdAt = now,
      modifiedAt = now,
      path = boundEntryPoint.path.toString,
      name = s"Entry point ${boundEntryPoint.entryId}",
      description = s"Imported entry point ${boundEntryPoint.entryId}"
    )
  }

  private def getEntryPointFileToDataStoreJobFile(
      boundEntryPoint: BoundEntryPoint,
      jobId: UUID): DataStoreJobFile = {
    DataStoreJobFile(jobId, entryPointFileToDataStoreFile(boundEntryPoint))
  }

  // Need to thread through the BoundEntryPoint for the EntryId
  private def getEntryPointDataStoreFiles(jobId: UUID,
                                          importPath: Path,
                                          entryPoints: Seq[BoundEntryPoint])
    : Seq[(BoundEntryPoint, DataStoreJobFile)] = {
    entryPoints.map { e =>
      val ep = e.copy(path = importPath.resolve(e.path))
      val dsj = getEntryPointFileToDataStoreJobFile(ep, jobId)
      (e, dsj)
    }
  }

  // The datasetId is explicitly set to only Int because of the how works. The SL UI should fix this.
  private def toBoundServiceEntryPoint(
      boundEntryPoint: BoundEntryPoint,
      fileTypeId: String,
      datasetId: Int): BoundServiceEntryPoint = {
    BoundServiceEntryPoint(boundEntryPoint.entryId, fileTypeId, datasetId)
  }

  private def getOrImportEntryPoint(dao: JobsDao,
                                    entryPointFile: DataStoreJobFile,
                                    boundEntryPoint: BoundEntryPoint)
    : Future[(BoundServiceEntryPoint, DataSetMetaDataSet)] = {

    val uuid = entryPointFile.dataStoreFile.uniqueId
    val fileType = entryPointFile.dataStoreFile.fileTypeId

    def andLog(ds: DataSetMetaDataSet): Future[DataSetMetaDataSet] = Future {
      // This should be to the import Job writer
      logger.info(
        s"Entry point dataset ${uuid.toString} already present in database. Skipping file.")
      ds
    }

    def importNewEntry(f: DataStoreJobFile)
      : Future[(BoundServiceEntryPoint, DataSetMetaDataSet)] =
      for {
        _ <- dao.addDataStoreFile(entryPointFile)
        ds <- dao.getDataSetMetaData(uuid)
      } yield
        (toBoundServiceEntryPoint(boundEntryPoint,
                                  f.dataStoreFile.fileTypeId,
                                  ds.id),
         ds)

    dao
      .getDataSetMetaData(uuid)
      .flatMap(andLog)
      .map { f =>
        //FIXME THIS NEEDS TO BE REMOVED
        val entryId =
          PbsmrtpipeConstants.metaTypeToEntryId(fileType).getOrElse("unknown")
        (BoundServiceEntryPoint(entryId, fileType, f.id), f)
      }
      .recoverWith {
        case _: ResourceNotFoundError => importNewEntry(entryPointFile)
      }
  }

  private def getOrImportEntryPoints(
      dao: JobsDao,
      entryPointFiles: Seq[(BoundEntryPoint, DataStoreJobFile)])
    : Future[Seq[(BoundServiceEntryPoint, DataSetMetaDataSet)]] =
    Future.sequence(
      entryPointFiles.map(f => getOrImportEntryPoint(dao, f._2, f._1)))

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

  private def addEntryPoints(
      dao: JobsDao,
      jobId: Int, // unzipped job
      epDsFiles: Seq[DataStoreJobFile]): Future[Seq[EngineJobEntryPoint]] = {
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

  /**
    * The original EntryPoints in the exported SMRT Link Job need to be updated
    * to be set to the correct values of the current SL system.
    *
    * @param jsObject Original jsonSettings of the job to import
    * @param entryPoints New (already imported) Entry Points
    */
  private def updateJsonSettings(
      jsObject: JsObject,
      entryPoints: Seq[BoundServiceEntryPoint]): JsObject = {

    val update = JsObject("entryPoints" -> entryPoints.toJson)

    new JsObject(jsObject.fields ++ update.fields)
  }

  private def updateEngineJobSettings(dao: JobsDao,
                                      jobId: IdAble,
                                      jsObject: JsObject): Future[String] = {
    dao
      .updateJsonSettings(jobId, jsObject)
      .map(_ => s"Updated Job ${jobId.toIdString} JsonSettings")
  }

  private def addJobEvents(dao: JobsDao,
                           events: Seq[JobEvent]): Future[String] = {
    events match {
      case Nil => Future.successful("No events to import")
      case _ =>
        dao
          .addJobEvents(events)
          .map(_ => s"Imported ${events.length} JobEvents")
    }
  }

  /**
    * Importing a Job from a ZIP file requires several steps to import
    * difference data sources to make the imported Job to be indistinguishable. However, there's an important difference
    * that the imported (DataSet) Entry Points will not have the computed reports that are produced by "import-dataset"
    * job.
    *
    * To avoid confusion with the different jobs.
    * - thisJob
    * - rawJob (raw exported Job)
    * - newJob (newly created imported Job where the raw job will be imported to)
    *
    * The "Entry Points" from raw exported job (jr) will be imported from j0 (via DataStore)
    *
    * 1. (Bootstrapping) By importing the "raw" EngineJob (jr) to create jc and getting the Job (Int) Id and Path (to write to)
    * 2. Extracting the output of the zip to j1 Path (from #1)
    * 3. Extract the Entry Points from the zip, update the job id to be j0 (this mimics that the DataStore files were
    *   "created" by the j0. This is somewhat similar to ImportDataSet job. However, the reports will are computed.
    */
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

    def andLog(sx: String): Future[String] = Future {
      resultsWriter.writeLine(sx)
      sx
    }

    // for testing we need to be able to swap in a new UUID
    def getU(uuid: UUID): UUID = getUuid(uuid, opts.mockJobId.getOrElse(false))

    val logFile = getStdOutLog(resources, dao)
    val manifest = getManifest(opts.zipPath)

    val jobDsFiles =
      manifest.datastore.map(_.files).getOrElse(Seq.empty[DataStoreFile])

    def fx2: Future[PacBioDataStore] =
      for {
        thisJob <- dao.getJobById(resources.jobId)
        rawJob <- Future.successful(
          manifest.job.copy(uuid = getU(manifest.job.uuid),
                            projectId = thisJob.projectId))
        _ <- andLog(s"Successfully extracted job from manifest $rawJob")
        newJob <- dao.importRawEngineJob(rawJob)
        importedPath <- Future.successful(Paths.get(newJob.path))
        jobSummary <- expandJobF(opts.zipPath, importedPath)
        _ <- andLog(
          s"Successfully extract ${jobSummary.nFiles} files to $importedPath")
        epDsFiles <- Future.successful(
          getEntryPointDataStoreFiles(resources.jobId,
                                      importedPath,
                                      manifest.entryPoints))
        entryPointDatasets <- getOrImportEntryPoints(dao, epDsFiles)
        _ <- addEntryPoints(dao, newJob.id, epDsFiles.map(_._2))
        _ <- addImportedJobFiles(dao, newJob, jobDsFiles)
        updatedJobEvents <- Future.successful(
          manifest.events
            .getOrElse(Seq.empty[JobEvent])
            .map(e => e.copy(jobId = newJob.id, eventId = getU(e.eventId))))
        msg <- addJobEvents(dao, updatedJobEvents)
        _ <- andLog(msg)
        _ <- updateEngineJobSettings(
          dao,
          newJob.id,
          updateJsonSettings(rawJob.jsonSettings.parseJson.asJsObject,
                             entryPointDatasets.map(_._1)))
        jobDsManifestJson <- Future.successful(
          toManifestDataStoreFile(
            importedPath.resolve(JobConstants.OUTPUT_EXPORT_MANIFEST_JSON)))
        ds <- Future.successful(
          writeFilesToDataStore(
            Seq(logFile, jobDsManifestJson) ++ epDsFiles.map(
              _._2.dataStoreFile)))
      } yield ds

    //FIXME This should be computed based on the file size.
    val maxTimeOut: FiniteDuration = 2.minutes
    convertTry(runAndBlock(fx2, maxTimeOut),
               resultsWriter,
               startedAt,
               resources.jobId)
  }
}
