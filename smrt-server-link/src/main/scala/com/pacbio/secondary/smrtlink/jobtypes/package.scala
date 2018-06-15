package com.pacbio.secondary.smrtlink

import java.nio.file.{Path, Paths}
import java.io.{PrintWriter, StringWriter}
import java.net.InetAddress
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetFileUtils,
  DataSetMetaTypes,
  DataSetUpdateUtils
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.jsonprotocols.{
  ServiceJobTypeJsonProtocols,
  SmrtLinkJsonProtocols
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.{
  BoundServiceEntryPoint,
  EngineJobEntryPointRecord
}
import com.pacbio.secondary.smrtlink.validators.ValidateServiceDataSetUtils
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Created by mkocher on 8/17/17.
  */
package object jobtypes {

  import com.pacbio.common.models.CommonModelImplicits._

  trait ServiceCoreJobModel
      extends LazyLogging
      with DataSetFileUtils
      with timeUtils {
    type Out
    val jobTypeId: JobTypeIds.JobType

    // This should be rethought
    def host = InetAddress.getLocalHost.getHostName

    /**
      * The Service Job has access to the DAO, but should not update or mutate the state of the current job (or any
      * other job). The ServiceRunner and EngineWorker actor will handle updating the state of the job.
      *
      * At the job level, the job is responsible for importing any data back into the system and sending
      * "Status" update events.
      *
      * @param resources     Resources for the Job to use (e.g., job id, root path) This needs to be expanded to include the system config.
      *                      This be renamed for clarity
      * @param resultsWriter Writer to write to job stdout and stderr
      * @param dao           interface to the DB. See above comments on suggested use and responsibility
      * @param config        System Job config. Any specific config for any job type needs to be pushed into this layer
      * @return
      */
    def run(resources: JobResourceBase,
            resultsWriter: JobResultsWriter,
            dao: JobsDao,
            config: SystemJobConfig): Either[ResultFailed, Out]

    // This is really clumsy and duplicated. This needs to be simplified.
    def convertTry[Out](tx: => Try[Out],
                        writer: JobResultsWriter,
                        startedAt: JodaDateTime,
                        jobUUID: UUID): Either[ResultFailed, Out] = {
      tx.toEither.left.map { ex =>
        val runTime = computeTimeDeltaFromNow(startedAt)
        val msg = s"Failed to Run ${ex.getMessage}"
        ResultFailed(jobUUID,
                     jobTypeId.id,
                     msg,
                     runTime,
                     AnalysisJobStates.FAILED,
                     host)
      }
    }

    // Util layer to get the ServiceJobRunner to compose better.
    def runTry(resources: JobResourceBase,
               resultsWriter: JobResultsWriter,
               dao: JobsDao,
               config: SystemJobConfig): Try[Out] = {
      Try {
        run(resources, resultsWriter, dao, config)
      } match {
        case Success(result) =>
          result match {
            case Right(rx) => Success(rx)
            case Left(rx) =>
              val msg = s"Failed to run job ${rx.message}"
              resultsWriter.writeLineError(msg)
              Failure(new Exception(msg))
          }
        case Failure(ex) =>
          val msg = s"Failed to run job ${ex.getMessage}"
          resultsWriter.writeLineError(msg)
          val sw = new StringWriter
          ex.printStackTrace(new PrintWriter(sw))
          resultsWriter.writeLineError(sw.toString)
          Failure(new Exception(msg))
      }
    }

    /**
      * Util to add Stdout/Log "Master" DataStore File
      *
      * @param dao Jobs DAO
      * @return
      */
    def addStdOutLogToDataStore(
        resources: JobResourceBase,
        dao: JobsDao,
        projectId: Option[Int]): Future[DataStoreFile] = {
      val now = JodaDateTime.now()
      val path = resources.path.resolve(JobConstants.JOB_STDOUT)
      val file = DataStoreFile(
        UUID.randomUUID(),
        JobConstants.DATASTORE_FILE_MASTER_LOG_ID,
        FileTypes.LOG.fileTypeId,
        // probably wrong; the file isn't closed yet.  But it won't get
        // closed until after this method completes.
        path.toFile.length,
        now,
        now,
        path.toString,
        isChunked = false,
        JobConstants.DATASTORE_FILE_MASTER_NAME,
        JobConstants.DATASTORE_FILE_MASTER_DESC
      )

      dao.importDataStoreFile(file, resources.jobId, projectId).map(_ => file)
    }

    def runAndBlock[T](fx: Future[T], timeOut: FiniteDuration): Try[T] =
      Try {
        Await.result(fx, timeOut)
      }

    /**
      * Update the entry point for a dataset to point to a new XML file
      * saved with metadata updated from the database.  Fails if any errors
      * are encountered.  Currently this only affects SubreadSets, but it
      * will pass through other dataset types silently.
      *
      * @param entryPoint resolved entry point path
      * @param jobPath output directory for the job
      * @param dao JobsDao object
      * @return Future with updated (or original) entry point path
      */
    def updateDataSetandWriteToEntryPointsDir(entryPoint: Path,
                                              jobPath: Path,
                                              dao: JobsDao): Future[Path] = {
      blocking {
        val dsMeta = getDataSetMiniMeta(entryPoint)
        dsMeta.metatype match {
          case DataSetMetaTypes.Subread =>
            val outDir = jobPath.resolve("entry-points")
            outDir.toFile.mkdirs
            val outPath =
              outDir.resolve(s"${dsMeta.uuid.toString}.subreadset.xml")
            dao.getSubreadDataSetById(dsMeta.uuid).flatMap { ds =>
              DataSetUpdateUtils.saveUpdatedCopy(
                entryPoint,
                outPath,
                Some(ds.bioSampleName),
                Some(ds.wellSampleName)) match {
                case None =>
                  Future.successful(outPath)
                case Some(err) =>
                  // FIXME ideally we would just fail in this case but the blast
                  // radius is potentially huge so I'm leaving it fault-tolerant
                  // until we can test it better
                  //Future.failed(new RuntimeException(err))
                  logger.error(s"Dataset ${dsMeta.uuid} could not be updated")
                  logger.error(err)
                  Future.successful(entryPoint)
              }
            }
          case dst =>
            logger.warn(
              s"Only Subreadsets are supported. Not adding/writing DataSet type $dst to entry-points dir.")
            Future.successful(entryPoint)
        }
      }
    }

    def resolvePathsAndWriteEntryPoints(
        dao: JobsDao,
        jobRoot: Path,
        datasetType: DataSetMetaTypes.DataSetMetaType,
        datasetIds: Seq[IdAble]): Future[Seq[Path]] = {
      for {
        datasets <- ValidateServiceDataSetUtils.resolveInputs(datasetType,
                                                              datasetIds,
                                                              dao)
        paths <- Future.successful(datasets.map(_.path))
        updatedPaths <- Future.sequence(paths.map { p =>
          updateDataSetandWriteToEntryPointsDir(Paths.get(p), jobRoot, dao)
        })
      } yield updatedPaths
    }
  }

  trait ServiceJobOptions {

    // This metadata will be used when creating an instance of a EngineJob
    val name: Option[String]
    val description: Option[String]
    val projectId: Option[Int]
    // To Enable default behavior for Core Jobs, Note, MultiJobs have a different Default Value.
    val submit: Option[Boolean]

    /**
      * NOTE. It's VERY important that these are all defs, NOT vals, otherwise you'll
      * see a runtime exception with the json serialization.
      *
      * @return
      */
    def subJobTypeId: Option[String] = None

    /**
      * This is a guess.
      *
      * This should capture both the fetching from the db as well as
      * writing to disk.
      */
    def TIMEOUT_PER_RECORD = 1.seconds

    // This is duplicated with projectId because of JSON optional options. It would be better if
    // "projectId" was private.
    def getProjectId(): Int =
      projectId.getOrElse(JobConstants.GENERAL_PROJECT_ID)

    def getSubmit(): Boolean =
      submit.getOrElse(JobConstants.SUBMIT_DEFAULT_CORE_JOB)

    // This needs to be defined at the job option level to be a globally unique type.
    def jobTypeId: JobTypeIds.JobType

    // This is a def for seralization reasons.
    def toJob(): ServiceCoreJob

    /**
      * This the default timeout for DAO operations.
      *
      * It's important this is a def, otherwise there will be runtime errors from spray serialization
      *
      * @return
      */
    def DEFAULT_TIMEOUT = 25.seconds

    /**
      * Job Option validation
      *
      * This should be relatively quick (e.g., not validation of 1G fasta file)
      *
      * Any time or resource consuming validation should be pushed to job run time.
      *
      *
      * TODO: Does this also need UserRecord passed in?
      *
      * Validate the Options (and make sure they're consistent within the system config if necessary)
      *
      * @return
      */
    def validate(dao: JobsDao,
                 config: SystemJobConfig): Option[InvalidJobOptionError]

    /**
      * Validation func for resolving a future and translating any errors to
      * the InvalidJobOption error.
      *
      * @param fx      Func to run
      * @param timeout timeout
      * @tparam T
      * @return
      */
    def validateOptionsAndBlock[T](
        fx: => Future[T],
        timeout: FiniteDuration): Option[InvalidJobOptionError] = {
      Try(Await.result(fx, timeout)) match {
        case Success(_) => None
        case Failure(ex) =>
          Some(
            InvalidJobOptionError(
              s"Failed option validation. ${ex.getMessage}"))
      }
    }

    /**
      * Common Util for resolving entry points
      *
      * Only DataSet types will be resolved, but the entry point is a general file type
      *
      * @param e   Bound Service Entry Point
      * @param dao db DAO
      * @return
      */
    def resolveEntry(
        e: BoundServiceEntryPoint,
        dao: JobsDao): Future[(EngineJobEntryPointRecord, BoundEntryPoint)] = {
      // Only DataSet types will be resolved, but the entry point is a general file type id

      for {
        datasetType <- ValidateServiceDataSetUtils.validateDataSetType(
          e.fileTypeId)
        d <- ValidateServiceDataSetUtils.resolveDataSet(datasetType,
                                                        e.datasetId,
                                                        dao)
      } yield
        (EngineJobEntryPointRecord(d.uuid, e.fileTypeId),
         BoundEntryPoint(e.entryId, Paths.get(d.path)))
    }

    def resolver(entryPoints: Seq[BoundServiceEntryPoint], dao: JobsDao)
      : Future[Seq[(EngineJobEntryPointRecord, BoundEntryPoint)]] =
      Future.sequence(entryPoints.map(ep => resolveEntry(ep, dao)))

    /**
      * This is used to communicate the EntryPoints used for the Job. This is abstract so that the model is explicit.
      *
      * @param dao JobsDoa
      * @return
      */
    def resolveEntryPoints(dao: JobsDao): Seq[EngineJobEntryPointRecord] =
      Seq.empty[EngineJobEntryPointRecord]

    def validateAndResolveEntryPoints(
        dao: JobsDao,
        datasetType: DataSetMetaTypes.DataSetMetaType,
        ids: Seq[IdAble]): Seq[EngineJobEntryPointRecord] = {
      val timeout: FiniteDuration = ids.length * TIMEOUT_PER_RECORD
      def fx =
        for {
          datasets <- ValidateServiceDataSetUtils.resolveInputs(datasetType,
                                                                ids,
                                                                dao)
          entryPoints <- Future.successful(datasets.map(ds =>
            EngineJobEntryPointRecord(ds.uuid, datasetType.toString)))
        } yield entryPoints

      Await.result(blocking(fx), timeout)
    }
  }

  abstract class ServiceCoreJob(opts: ServiceJobOptions)
      extends ServiceCoreJobModel {
    // sugar
    val jobTypeId = opts.jobTypeId

    // This really should NEVER be used
    def getStdOutLog(resources: JobResourceBase, dao: JobsDao): DataStoreFile =
      runAndBlock(addStdOutLogToDataStore(resources, dao, opts.projectId),
                  opts.DEFAULT_TIMEOUT).get

    def getStdOutLogT(resources: JobResourceBase,
                      dao: JobsDao): Try[DataStoreFile] = {
      runAndBlock(addStdOutLogToDataStore(resources, dao, opts.projectId),
                  opts.DEFAULT_TIMEOUT)
    }
  }

  // Use to encode a multi job type
  trait MultiJob

  trait ServiceMultiJobOptions extends ServiceJobOptions with MultiJob {
    // fighting with the type system here
    def toMultiJob(): ServiceMultiJobModel
  }

  abstract class ServiceMultiJob(opts: ServiceMultiJobOptions)
      extends ServiceCoreJobModel {
    val jobTypeId = opts.jobTypeId
  }

  trait ServiceMultiJobModel extends ServiceCoreJobModel {

    // This needs to be removed, or fixed. This is necessary for multi-jobs and is not used. `runWorkflow` is the method to call
    override def run(resources: JobResourceBase,
                     resultsWriter: JobResultsWriter,
                     dao: JobsDao,
                     config: SystemJobConfig) = {
      throw new Exception("Direct call of Run on MultiJob is not supported")
    }
  }

  trait Converters {

    import SmrtLinkJsonProtocols._
    import ServiceJobTypeJsonProtocols._

    /**
      * Load the JSON Settings from an Engine job and create the companion ServiceJobOption
      * instance.
      *
      * If there's a deseralization issue, this will raise.
      *
      * @param engineJob EngineJob
      * @tparam T ServiceJobOptions
      * @return
      */
    def convertServiceCoreJobOption[T >: ServiceJobOptions](
        engineJob: EngineJob): T = {

      val jx = engineJob.jsonSettings.parseJson

      // The EngineJob data model should be using a proper type
      val jobTypeId: JobTypeIds.JobType = JobTypeIds
        .fromString(engineJob.jobTypeId)
        .getOrElse(
          throw new IllegalArgumentException(
            s"Job type '${engineJob.jobTypeId}' is not supported"))

      convertToOption[T](jobTypeId, jx)
    }

    private def convertToOption[T >: ServiceJobOptions](
        jobTypeId: JobTypeIds.JobType,
        jx: JsValue): T = {

      jobTypeId match {
        case JobTypeIds.HELLO_WORLD => jx.convertTo[HelloWorldJobOptions]
        case JobTypeIds.DB_BACKUP => jx.convertTo[DbBackUpJobOptions]
        case JobTypeIds.DELETE_DATASETS =>
          jx.convertTo[DeleteDataSetJobOptions]
        case JobTypeIds.EXPORT_DATASETS =>
          jx.convertTo[ExportDataSetsJobOptions]
        case JobTypeIds.EXPORT_JOBS => jx.convertTo[ExportSmrtLinkJobOptions]
        case JobTypeIds.IMPORT_JOB => jx.convertTo[ImportSmrtLinkJobOptions]
        case JobTypeIds.IMPORT_DATASETS_ZIP =>
          jx.convertTo[ImportDataSetsZipJobOptions]
        case JobTypeIds.CONVERT_FASTA_BARCODES =>
          jx.convertTo[ImportBarcodeFastaJobOptions]
        case JobTypeIds.IMPORT_DATASET => jx.convertTo[ImportDataSetJobOptions]
        case JobTypeIds.CONVERT_FASTA_REFERENCE =>
          jx.convertTo[ImportFastaJobOptions]
        case JobTypeIds.CONVERT_FASTA_GMAPREFERENCE =>
          jx.convertTo[ImportFastaGmapJobOptions]
        case JobTypeIds.MERGE_DATASETS => jx.convertTo[MergeDataSetJobOptions]
        case JobTypeIds.MOCK_PBSMRTPIPE =>
          jx.convertTo[MockPbsmrtpipeJobOptions]
        case JobTypeIds.PBSMRTPIPE => jx.convertTo[PbsmrtpipeJobOptions]
        case JobTypeIds.SIMPLE => jx.convertTo[SimpleJobOptions]
        case JobTypeIds.CONVERT_RS_MOVIE =>
          jx.convertTo[RsConvertMovieToDataSetJobOptions]
        case JobTypeIds.DELETE_JOB => jx.convertTo[DeleteSmrtLinkJobOptions]
        case JobTypeIds.TS_JOB => jx.convertTo[TsJobBundleJobOptions]
        case JobTypeIds.TS_SYSTEM_STATUS =>
          jx.convertTo[TsSystemStatusBundleJobOptions]
        case JobTypeIds.TS_JOB_HARVESTER_JOB =>
          jx.convertTo[TsJobHarvesterJobOptions]
        case JobTypeIds.DS_COPY => jx.convertTo[CopyDataSetJobOptions]
        // These really need to be separated out into there own class
        case JobTypeIds.MJOB_MULTI_ANALYSIS =>
          jx.convertTo[MultiAnalysisJobOptions]
      }
    }

    private def convertToMultiCoreJobOptions[T >: ServiceMultiJobOptions](
        jobTypeId: JobTypeIds.JobType,
        jx: JsValue): T = {

      //FIXME(mpkocher)(9-12-2017) need to fix this at the type level
      jobTypeId match {
        case JobTypeIds.MJOB_MULTI_ANALYSIS =>
          jx.convertTo[MultiAnalysisJobOptions]
        case x =>
          throw new IllegalArgumentException(
            s"Job Type id '$x' is not a supported MultiJob type")
      }
    }

    def convertServiceMultiJobOption[T >: ServiceMultiJobOptions](
        engineJob: EngineJob): T = {
      val jx = engineJob.jsonSettings.parseJson

      // The EngineJob data model should be using a proper type
      val jobTypeId: JobTypeIds.JobType = JobTypeIds
        .fromString(engineJob.jobTypeId)
        .getOrElse(
          throw new IllegalArgumentException(
            s"Job type '${engineJob.jobTypeId}' is not supported"))

      convertToMultiCoreJobOptions[T](jobTypeId, jx)
    }
  }

  object Converters extends Converters

}
