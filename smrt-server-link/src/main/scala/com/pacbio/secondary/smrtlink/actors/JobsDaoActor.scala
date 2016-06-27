package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Path, Paths}
import java.security.MessageDigest
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import akka.util.Timeout
import com.pacbio.common.actors.{ActorRefFactoryProvider, PacBioActor}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.converters.ReferenceInfoConverter
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.engine.CommonMessages._
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.engine.actors.{EngineActorCore, EngineWorkerActor, QuickEngineWorkerActor}
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates.Completed
import com.pacbio.secondary.analysis.jobs.JobModels.{DataStoreJobFile, PacBioDataStore, _}
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.{Converters, EngineJobEntryPointRecord, ProjectRequest, ProjectUserRequest, ReferenceServiceDataSet, GmapReferenceServiceDataSet}
import org.joda.time.{DateTime => JodaDateTime}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object MessageTypes {
  abstract class ProjectMessage

  abstract class JobMessage

  abstract class DataSetMessage

  abstract class DataStoreMessage
}

object JobsDaoActor {
  import MessageTypes._

  // Project
  case object GetProjects extends ProjectMessage
  case class GetProjectById(projId: Int) extends ProjectMessage
  case class CreateProject(opts: ProjectRequest) extends ProjectMessage
  case class UpdateProject(projId: Int, opts: ProjectRequest) extends ProjectMessage
  case class GetProjectUsers(projId: Int) extends ProjectMessage
  case class AddProjectUser(projId: Int, user: ProjectUserRequest) extends ProjectMessage
  case class DeleteProjectUser(projId: Int, user: String) extends ProjectMessage
  case class GetDatasetsByProject(projId: Int) extends ProjectMessage
  case class GetUserProjects(login: String)
  case class GetUserProjectsDatasets(user: String) extends ProjectMessage
  case class SetProjectForDatasetId(dsId: Int, projId: Int) extends ProjectMessage
  case class SetProjectForDatasetUuid(dsId: UUID, projId: Int) extends ProjectMessage

  // Job
  case object GetAllJobs extends JobMessage

  case class GetJobById(jobId: Int) extends JobMessage

  case class GetJobByUUID(jobId: UUID) extends JobMessage

  case class GetJobsByJobType(jobTypeId: String) extends JobMessage

  case class GetJobEventsByJobId(jobId: Int) extends JobMessage

  case class UpdateJobState(jobId: Int, state: AnalysisJobStates.JobStates, message: String) extends JobMessage

  case class CreateJobType(
      uuid: UUID,
      name: String,
      comment: String,
      jobTypeId: String,
      coreJob: CoreJob,
      engineEntryPoints: Option[Seq[EngineJobEntryPointRecord]] = None,
      jsonSettings: String,
      createdBy: Option[String]) extends JobMessage


  // Get all DataSet Entry Points
  case class GetEngineJobEntryPoints(jobId: Int)

  // DataSet
  case object GetDataSetTypes extends DataSetMessage

  case class GetDataSetTypeById(i: String) extends DataSetMessage

  // Get all DataSet Metadata records
  case class GetDataSetMetaById(i: Int) extends DataSetMessage

  case class GetDataSetMetaByUUID(uuid: UUID) extends DataSetMessage

  // DS Subreads
  case class GetSubreadDataSets(limit: Int) extends DataSetMessage

  case class GetSubreadDataSetById(i: Int) extends DataSetMessage

  case class GetSubreadDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetSubreadDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetSubreadDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // DS Reference
  case class GetReferenceDataSets(limit: Int) extends DataSetMessage

  case class GetReferenceDataSetById(i: Int) extends DataSetMessage

  case class GetReferenceDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetReferenceDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetReferenceDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // GMAP reference
  case class GetGmapReferenceDataSets(limit: Int) extends DataSetMessage

  case class GetGmapReferenceDataSetById(i: Int) extends DataSetMessage

  case class GetGmapReferenceDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetGmapReferenceDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetGmapReferenceDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // Alignment
  case class GetAlignmentDataSetById(i: Int) extends DataSetMessage

  case class GetAlignmentDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetAlignmentDataSets(limit: Int)

  // Hdf Subreads
  case class GetHdfSubreadDataSetById(i: Int) extends DataSetMessage

  case class GetHdfSubreadDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetHdfSubreadDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetHdfSubreadDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  case class GetHdfSubreadDataSets(limit: Int) extends DataSetMessage

  // CCS reads
  case class GetConsensusReadDataSetsById(i: Int) extends DataSetMessage

  case class GetConsensusReadDataSetsByUUID(uuid: UUID) extends DataSetMessage

  case class GetConsensusReadDataSets(limit: Int) extends DataSetMessage

  // CCS alignments
  case class GetConsensusAlignmentDataSetsById(i: Int) extends DataSetMessage

  case class GetConsensusAlignmentDataSetsByUUID(uuid: UUID) extends DataSetMessage

  case class GetConsensusAlignmentDataSets(limit: Int) extends DataSetMessage

  // Barcode DataSets
  case class GetBarcodeDataSets(limit: Int) extends DataSetMessage

  case class GetBarcodeDataSetsById(i: Int) extends DataSetMessage

  case class GetBarcodeDataSetsByUUID(uuid: UUID) extends DataSetMessage

  case class GetBarcodeDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetBarcodeDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // ContigSet
  case class GetContigDataSets(limit: Int) extends DataSetMessage

  case class GetContigDataSetsById(i: Int) extends DataSetMessage

  case class GetContigDataSetsByUUID(uuid: UUID) extends DataSetMessage

  // Import a Reference Dataset
  case class ImportReferenceDataSet(ds: ReferenceServiceDataSet) extends DataSetMessage

  case class ImportReferenceDataSetFromFile(path: String) extends DataSetMessage

  // Import a GMAP reference
  case class ImportGmapReferenceDataSet(ds: GmapReferenceServiceDataSet) extends DataSetMessage

  case class ImportGmapReferenceDataSetFromFile(path: String) extends DataSetMessage

  // This should probably be a different actor
  // Convert a Reference to a Dataset and Import
  case class ConvertReferenceInfoToDataset(path: String, dsPath: Path) extends DataSetMessage

  case class ImportSubreadDataSetFromFile(path: String) extends DataSetMessage


  // DataStore Files
  case class GetDataStoreFileByUUID(uuid: UUID) extends DataStoreMessage

  case class GetDataStoreServiceFilesByJobId(i: Int) extends DataStoreMessage

  case class GetDataStoreReportFileByJobId(jobId: Int) extends DataStoreMessage

  case class GetDataStoreReportByUUID(uuid: UUID) extends DataStoreMessage

  case class GetDataStoreFiles(limit: Int = 1000) extends DataStoreMessage

  case class GetDataStoreFilesByJobId(i: Int) extends DataStoreMessage

  case class GetDataStoreFilesByJobUUID(i: UUID) extends DataStoreMessage
}

class JobsDaoActor(dao: JobsDao, val engineConfig: EngineConfig, val resolver: JobResourceResolver) extends PacBioActor with EngineActorCore with ActorLogging {

  import JobsDaoActor._

  final val QUICK_TASK_IDS = Set(JobTypeId("import_dataset"), JobTypeId("merge_dataset"))

  implicit val timeout = Timeout(5.second)

  val logStatusInterval = if (engineConfig.debugMode) 1.minute else 10.minutes

  //MK Probably want to have better model for this
  val checkForWorkInterval = 5.seconds

  val checkForWorkTick = context.system.scheduler.schedule(10.seconds, checkForWorkInterval, self, CheckForRunnableJob)

  // Log the job summary. This should probably be in a health agent
  val tick = context.system.scheduler.schedule(10.seconds, logStatusInterval, self, GetSystemJobSummary)

  // Keep track of workers
  val workers = mutable.Queue[ActorRef]()

  val maxNumQuickWorkers = 10
  // For jobs that are small and can completed in a relatively short amount of time (~seconds) and have minimal resource usage
  val quickWorkers = mutable.Queue[ActorRef]()

  // This is not awesome
  val jobRunner = new SimpleAndImportJobRunner(self)


  override def preStart(): Unit = {
    log.info(s"Starting engine manager actor $self with $engineConfig")

    (0 until engineConfig.maxWorkers).foreach { x =>
      val worker = context.actorOf(EngineWorkerActor.props(self, jobRunner), s"engine-worker-$x")
      workers.enqueue(worker)
      log.debug(s"Creating worker $worker")
    }

    (0 until maxNumQuickWorkers).foreach { x =>
      val worker = context.actorOf(QuickEngineWorkerActor.props(self, jobRunner), s"engine-quick-worker-$x")
      quickWorkers.enqueue(worker)
      log.debug(s"Creating Quick worker $worker")
    }
  }

  override def preRestart(reason:Throwable, message:Option[Any]){
    super.preRestart(reason, message)
    log.error(s"$self (pre-restart) Unhandled exception ${reason.getMessage} Message $message")
  }

  // This should return a Future
  def addJobToWorker(runnableJobWithId: RunnableJobWithId, workerQueue: mutable.Queue[ActorRef]): Unit = {

    if (workerQueue.nonEmpty) {
      log.info(s"Checking for work. numWorkers ${workerQueue.size}, numQuickWorkers ${quickWorkers.size}")
      log.info(s"Found jobOptions work ${runnableJobWithId.job.jobOptions.toJob.jobTypeId}. Updating state and starting task.")

      // This should be extended to support a list of Status Updates, to avoid another ask call and a separate db call
      // e.g., UpdateJobStatus(runnableJobWithId.job.uuid, Seq(AnalysisJobStates.SUBMITTED, AnalysisJobStates.RUNNING)
      val f = for {
        m1 <- dao.updateJobStateByUUID(runnableJobWithId.job.uuid, AnalysisJobStates.SUBMITTED, "Updated to Submitted")
        m2 <- dao.updateJobStateByUUID(runnableJobWithId.job.uuid, AnalysisJobStates.RUNNING, "Updated to Running")
      } yield s"$m1,$m2"

      f onComplete {
        case Success(_) =>
          val worker = workerQueue.dequeue()
          val outputDir = resolver.resolve(runnableJobWithId)
          worker ! RunJob(runnableJobWithId.job, outputDir)
        case Failure(ex) =>
          log.error(s"addJobToWorker Unable to update state ${runnableJobWithId.job.uuid} Marking as Failed. Error ${ex.getMessage}")
          self ! UpdateJobStatus(runnableJobWithId.job.uuid, AnalysisJobStates.FAILED)
      }
    }
  }

  // This should return a future
  def checkForWork(): Unit = {
    //log.info(s"Checking for work. # of Workers ${workers.size} # Quick Workers ${quickWorkers.size} ")

    if (workers.nonEmpty || quickWorkers.nonEmpty) {
      val f = dao.getNextRunnableJobWithId

      f onSuccess {
        case Right(runnableJob) =>
          val jobType = runnableJob.job.jobOptions.toJob.jobTypeId
          if ((QUICK_TASK_IDS contains jobType) && quickWorkers.nonEmpty) addJobToWorker(runnableJob, quickWorkers)
          else addJobToWorker(runnableJob, workers)
        case Left(e) => log.debug(s"No work found. ${e.message}")
      }

      f onFailure {
        case e => log.error(s"Failure checking for new work ${e.getMessage}")
      }
    } else {
      log.debug("No available workers.")
    }
  }


  def toMd5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.getBytes).map("%02x".format(_)).mkString

  def toE(msg: String) = throw new ResourceNotFoundError(msg)

  def receive: Receive = {


    case CheckForRunnableJob =>
      //FIXME. This Try is probably not necessary
      Try {
        checkForWork()
      } match {
        case Success(_) =>
        case Failure(ex) => log.error(s"Failed check for runnable jobs ${ex.getMessage}")
      }

    case UpdateJobCompletedResult(result, workerType) =>
      log.info(s"Worker $workerType completed $result")
      workerType match {
        case QuickWorkType => quickWorkers.enqueue(sender)
        case StandardWorkType => workers.enqueue(sender)
      }

      val f = dao.updateJobStateByUUID(result.uuid, result.state, s"Updated to ${result.state}")

      f onComplete {
        case Success(_) =>
          log.info(s"Successfully updated job ${result.uuid} to ${result.state}")
          self ! CheckForRunnableJob
        case Failure(ex) =>
          log.error(s"Failed to update job ${result.uuid} state to ${result.state} Error ${ex.getMessage}")
          self ! CheckForRunnableJob
      }

    case GetSystemJobSummary =>
      dao.getJobs(1000).map { rs =>
        val states = rs.map(_.state).toSet
        // Summary of job states by type
        val results = states.toList.map(x => (x, rs.count(e => e.state == x)))
        log.debug(s"Results $results")
        dao.getDataStoreFiles.map { dsJobFiles =>
          log.debug(s"Number of DataStore files ${dsJobFiles.size}")
          dsJobFiles.foreach { x => log.debug(s"DS File $x") }
          rs.foreach { x => log.debug(s"Job result id ${x.id} -> ${x.uuid} ${x.state} ${x.jobTypeId}") }
        }
      }

    case GetJobStatusByUUID(uuid) => dao.getJobByUUID(uuid) pipeTo sender

    case AddNewJob(job) => dao.addRunnableJob(RunnableJob(job, AnalysisJobStates.CREATED))
        .map(_ => SuccessMessage(s"Successfully added Job ${job.uuid}")) pipeTo sender

    case HasNextRunnableJobWithId => dao.getNextRunnableJobWithId pipeTo sender

    //case UpdateJobStatus(uuid, state) => dao.updateJobStateByUUID(uuid, state) pipeTo sender

    case ImportDataStoreFile(dataStoreFile, jobUUID) =>
      log.debug(s"ImportDataStoreFile importing datastore file $dataStoreFile for job ${jobUUID.toString}")
      dao.addDataStoreFile(DataStoreJobFile(jobUUID, dataStoreFile)).flatMap {
        case Right(m) => Future { m.message }
        case Left(m) => Future.failed(new Exception(s"Failed to import datastore ${m.message}"))
      } pipeTo sender

    case PacBioImportDataSet(x, jobUUID) =>

      val thisSender = sender()

      x match {
        case ds: DataStoreFile =>
          log.info(s"PacbioImportDataset importing dataset from $ds")
          dao.addDataStoreFile(DataStoreJobFile(jobUUID, ds)) pipeTo thisSender

        case ds: PacBioDataStore =>
          log.info(s"loading files from datastore $ds")
          val results = Future.sequence(ds.files.map { f => dao.addDataStoreFile(DataStoreJobFile(jobUUID, f)) })
          val failures = results.map(_.map(x => x.left.toOption).filter(_.isDefined))
          failures.map { f =>
            if (f.nonEmpty) {
              Left(FailedMessage(s"Failed to import datastore $failures"))
            } else {
              Right(SuccessMessage(s"Successfully imported files from PacBioDataStore $x"))
            }
          } pipeTo thisSender
      }

    // End of EngineDaoActor

    case GetProjects => pipeWith(dao.getProjects(1000))
    case GetProjectById(projId: Int) => pipeWith {
      dao.getProjectById(projId).map(_.getOrElse(toE(s"Unable to find project $projId")))
    }
    case CreateProject(opts: ProjectRequest) => pipeWith(dao.createProject(opts))
    case UpdateProject(projId: Int, opts: ProjectRequest) => pipeWith {
      dao.updateProject(projId, opts).map(_.getOrElse(toE(s"Unable to find project $projId")))
    }
    case GetProjectUsers(projId: Int) =>
      pipeWith(dao.getProjectUsers(projId))
    case AddProjectUser(projId: Int, user: ProjectUserRequest) =>
      pipeWith(dao.addProjectUser(projId, user))
    case DeleteProjectUser(projId: Int, user: String) =>
      pipeWith(dao.deleteProjectUser(projId, user))
    case GetDatasetsByProject(projId: Int) =>
      pipeWith(dao.getDatasetsByProject(projId))
    case GetUserProjects(login: String) =>
      pipeWith(dao.getUserProjects(login))
    case GetUserProjectsDatasets(user: String) =>
      pipeWith(dao.getUserProjectsDatasets(user))
    case SetProjectForDatasetId(dsId: Int, projId: Int) =>
      pipeWith(dao.setProjectForDatasetId(dsId, projId))
    case SetProjectForDatasetUuid(dsId: UUID, projId: Int) =>
      pipeWith(dao.setProjectForDatasetUuid(dsId, projId))


    case GetAllJobs => pipeWith(dao.getJobs(1000))

    case GetJobsByJobType(jobTypeId) => pipeWith(dao.getJobsByTypeId(jobTypeId))

    case GetJobById(jobId: Int) => pipeWith {
      dao.getJobById(jobId).map(_.getOrElse(toE(s"Unable to find JobId $jobId")))
    }

    case GetJobByUUID(uuid) => pipeWith {
      dao.getJobByUUID(uuid).map(_.getOrElse(toE(s"Unable to find job ${uuid.toString}")))
    }

    case GetJobEventsByJobId(jobId: Int) => pipeWith(dao.getJobEventsByJobId(jobId))

    case GetDataSetMetaById(i: Int) => pipeWith {
      dao.getDataSetById(i).map(_.getOrElse(toE(s"Unable to find dataset $i")))
    }

    case GetDataSetMetaByUUID(i: UUID) => pipeWith {
      dao.getDataSetByUUID(i).map(_.getOrElse(toE(s"Unable to find dataset $i")))
    }

    // DataSet Types
    case GetDataSetTypes => pipeWith(dao.getDataSetTypes)

    case GetDataSetTypeById(n: String) => pipeWith {
      dao.getDataSetTypeById(n).map(_.getOrElse(toE(s"Unable to find dataset type '$n")))
    }

    // Get Subreads
    case GetSubreadDataSets(limit: Int) => pipeWith(dao.getSubreadDataSets(limit))

    case GetSubreadDataSetById(n: Int) => pipeWith {
      dao.getSubreadDataSetById(n).map(_.getOrElse(toE(s"Unable to find subread dataset '$n")))
    }

    case GetSubreadDataSetByUUID(uuid: UUID) => pipeWith {
      dao.getSubreadDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find subread dataset '$uuid")))
    }

    case GetSubreadDataSetDetailsById(n: Int) => pipeWith {
      dao.getSubreadDataSetDetailsById(n).map(_.getOrElse(toE(s"Unable to find subread dataset '$n")))
    }

    case GetSubreadDataSetDetailsByUUID(uuid: UUID) => pipeWith {
      dao.getSubreadDataSetDetailsByUUID(uuid).map(_.getOrElse(toE(s"Unable to find subread dataset ${uuid.toString}")))
    }

    // Get References
    case GetReferenceDataSets(limit: Int) => pipeWith(dao.getReferenceDataSets(limit))

    case GetReferenceDataSetById(id: Int) => pipeWith {
      dao.getReferenceDataSetById(id).map(_.getOrElse(toE(s"Unable to find reference dataset '$id")))
    }

    case GetReferenceDataSetByUUID(uuid: UUID) => pipeWith {
      dao.getReferenceDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find reference dataset '$uuid")))
    }

    case GetReferenceDataSetDetailsById(id: Int) => pipeWith {
      dao.getReferenceDataSetDetailsById(id).map(_.getOrElse(toE(s"Unable to find reference details dataset '$id")))
    }

    case GetReferenceDataSetDetailsByUUID(id: UUID) => pipeWith {
      dao.getReferenceDataSetDetailsByUUID(id).map(_.getOrElse(toE(s"Unable to find reference details dataset '$id")))
    }

    // Get GMAP References
    case GetGmapReferenceDataSets(limit: Int) => pipeWith(dao.getGmapReferenceDataSets(limit))

    case GetGmapReferenceDataSetById(id: Int) => pipeWith {
      dao.getGmapReferenceDataSetById(id).map(_.getOrElse(toE(s"Unable to find reference dataset '$id")))
    }

    case GetGmapReferenceDataSetByUUID(uuid: UUID) => pipeWith {
      dao.getGmapReferenceDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find reference dataset '$uuid")))
    }

    case GetGmapReferenceDataSetDetailsById(id: Int) => pipeWith {
      dao.getGmapReferenceDataSetDetailsById(id).map(_.getOrElse(toE(s"Unable to find reference details dataset '$id")))
    }

    case GetGmapReferenceDataSetDetailsByUUID(id: UUID) => pipeWith {
      dao.getGmapReferenceDataSetDetailsByUUID(id).map(_.getOrElse(toE(s"Unable to find reference details dataset '$id")))
    }

    case ImportReferenceDataSet(ds: ReferenceServiceDataSet) => pipeWith {
      log.debug("creating reference dataset")
      dao.insertReferenceDataSet(ds)
    }

    case ImportReferenceDataSetFromFile(path: String) => pipeWith {
      // FIXME. This should be removed
      val createdAt = JodaDateTime.now()
      val r = DataSetLoader.loadReferenceSet(Paths.get(path))
      val uuid = UUID.fromString(r.getUniqueId)
      val ds = ReferenceServiceDataSet(
        -99,
        uuid,
        r.getName,
        path,
        createdAt, createdAt,
        r.getDataSetMetadata.getNumRecords,
        r.getDataSetMetadata.getTotalLength,
        "0.5.0",
        "reference comments",
        "reference-tags",
        toMd5(uuid.toString),
        1,
        1,
        1,
        r.getDataSetMetadata.getPloidy,
        r.getDataSetMetadata.getOrganism)

      dao.insertReferenceDataSet(ds)
    }

    case ImportGmapReferenceDataSet(ds: GmapReferenceServiceDataSet) => pipeWith {
      log.debug("creating reference dataset")
      dao.insertGmapReferenceDataSet(ds)
    }

    case ImportGmapReferenceDataSetFromFile(path: String) => pipeWith {
      // FIXME. This should be removed
      val createdAt = JodaDateTime.now()
      val r = DataSetLoader.loadGmapReferenceSet(Paths.get(path))
      val uuid = UUID.fromString(r.getUniqueId)
      val ds = GmapReferenceServiceDataSet(
        -99,
        uuid,
        r.getName,
        path,
        createdAt, createdAt,
        r.getDataSetMetadata.getNumRecords,
        r.getDataSetMetadata.getTotalLength,
        "0.5.0",
        "reference comments",
        "reference-tags",
        toMd5(uuid.toString),
        1,
        1,
        1,
        r.getDataSetMetadata.getPloidy,
        r.getDataSetMetadata.getOrganism)

      dao.insertGmapReferenceDataSet(ds)
    }

    // get Alignments
    case GetAlignmentDataSets(limit: Int) => pipeWith(dao.getAlignmentDataSets(limit))

    case GetAlignmentDataSetById(n: Int) => pipeWith {
      dao.getAlignmentDataSetById(n).map(_.getOrElse(toE(s"Unable to find Alignment dataset '$n")))
    }

    case GetAlignmentDataSetByUUID(uuid: UUID) => pipeWith {
      dao.getAlignmentDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find Alignment dataset '$uuid")))
    }

    // Get HDF Subreads
    case GetHdfSubreadDataSets(limit: Int) => pipeWith(dao.getHdfDataSets(limit))

    case GetHdfSubreadDataSetById(n: Int) => pipeWith {
      dao.getHdfDataSetById(n).map(_.getOrElse(toE(s"Unable to find Hdf subread dataset '$n")))
    }

    case GetHdfSubreadDataSetByUUID(uuid: UUID) => pipeWith {
      dao.getHdfDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find Hdf subread dataset '$uuid")))
    }

    case GetHdfSubreadDataSetDetailsById(n: Int) => pipeWith {
      dao.getHdfDataSetDetailsById(n).map(_.getOrElse(toE(s"Unable to find Hdf subread dataset '$n")))
    }

    case GetHdfSubreadDataSetDetailsByUUID(uuid: UUID) => pipeWith {
      dao.getHdfDataSetDetailsByUUID(uuid).map(_.getOrElse(toE(s"Unable to find Hdf subread dataset '$uuid")))
    }

    // Get CCS Subreads
    case GetConsensusReadDataSets(limit: Int) => pipeWith(dao.getCCSDataSets(limit))

    case GetConsensusReadDataSetsById(n: Int) => pipeWith {
      dao.getCCSDataSetById(n).map(_.getOrElse(toE(s"Unable to find Hdf subread dataset '$n")))
    }

    case GetConsensusReadDataSetsByUUID(uuid: UUID) => pipeWith {
      dao.getCCSDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find Hdf subread dataset '$uuid")))
    }

    // Get CCS Subreads
    case GetConsensusAlignmentDataSets(limit: Int) => pipeWith(dao.getConsensusAlignmentDataSets(limit))

    case GetConsensusAlignmentDataSetsById(n: Int) => pipeWith {
      dao.getConsensusAlignmentDataSetById(n).map(_.getOrElse(toE(s"Unable to find ConsensusAlignmentSet '$n")))
    }

    case GetConsensusAlignmentDataSetsByUUID(uuid: UUID) => pipeWith {
      dao.getConsensusAlignmentDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find ConsensusAlignmentSet '$uuid")))
    }

    // Get Barcodes
    case GetBarcodeDataSets(limit: Int) => pipeWith(dao.getBarcodeDataSets(limit))

    case GetBarcodeDataSetsById(n: Int) => pipeWith {
      dao.getBarcodeDataSetById(n).map(_.getOrElse(toE(s"Unable to find Barcode dataset '$n")))
    }

    case GetBarcodeDataSetsByUUID(uuid: UUID) => pipeWith {
      dao.getBarcodeDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find Barcode dataset '$uuid")))
    }

    case GetBarcodeDataSetDetailsByUUID(uuid) => pipeWith {
      dao.getBarcodeDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find Barcode dataset Details for '$uuid")))
    }

    case GetBarcodeDataSetDetailsById(i) => pipeWith {
      dao.getBarcodeDataSetById(i).map(_.getOrElse(toE(s"Unable to find Barcode dataset Details for '$i")))
    }

    // Contigs
    case GetContigDataSets(limit: Int) => pipeWith(dao.getContigDataSets(limit))

    case GetContigDataSetsById(n: Int) => pipeWith {
      dao.getContigDataSetById(n).map(_.getOrElse(toE(s"Unable to find Contig dataset '$n")))
    }

    case GetContigDataSetsByUUID(uuid: UUID) => pipeWith {
      dao.getContigDataSetByUUID(uuid).map(_.getOrElse(toE(s"Unable to find Contig dataset '$uuid")))
    }


    case ConvertReferenceInfoToDataset(path: String, dsPath: Path) => respondWith {
      log.info(s"Converting reference.info.xml to dataset XML $path")
      ReferenceInfoConverter.convertReferenceInfoXMLToDataset(path, dsPath) match {
        case Right(ds) => s"Successfully converted reference.info.xml to dataset and imported ${ds.dataset.metadata.uuid} path:$dsPath"
        case Left(e) => throw new Exception(s"DataSetConversionError: Unable to convert and import $path. Error ${e.msg}")
      }
    }

    case ImportSubreadDataSetFromFile(path: String) => pipeWith {
      log.info(s"Importing subread dataset from $path")
      //val d = SubreadDataset.loadFrom(Paths.get(path).toUri)
      val pathP = Paths.get(path)
      val userId = 1
      val projectId = 1
      val jobId = 1
      val d = DataSetLoader.loadSubreadSet(Paths.get(path))
      val serviceDataSet = Converters.convert(d, pathP, userId, jobId, projectId)
      dao.insertSubreadDataSet(serviceDataSet)
    }

    // DataStore Files
    case GetDataStoreFiles(limit) => pipeWith(dao.getDataStoreFiles2)

    case GetDataStoreFileByUUID(uuid: UUID) => pipeWith {
      dao.getDataStoreFileByUUID2(uuid).map(_.getOrElse(toE(s"Unable to find DataStoreFile ${uuid.toString}")))
    }

    case GetDataStoreFilesByJobId(jobId) => pipeWith(dao.getDataStoreFilesByJobId(jobId))

    case GetDataStoreServiceFilesByJobId(jobId: Int) => pipeWith(dao.getDataStoreServiceFilesByJobId(jobId))

    // Reports
    case GetDataStoreReportFileByJobId(jobId: Int) => pipeWith(dao.getDataStoreReportFilesByJobId(jobId))

    case GetDataStoreReportByUUID(reportUUID: UUID) => pipeWith {
      dao.getDataStoreReportByUUID(reportUUID).map(_.getOrElse(toE(s"Unable to find report ${reportUUID.toString}")))
    }

    case GetDataStoreFilesByJobUUID(id) => pipeWith(dao.getDataStoreFilesByJobUUID(id))

    case ImportDataStoreFileByJobId(dsf: DataStoreFile, jobId) => pipeWith(dao.insertDataStoreFileById(dsf, jobId))

    case CreateJobType(uuid, name, pipelineId, jobTypeId, coreJob, entryPointRecords, jsonSettings, createdBy) =>
      pipeWith(dao.createJob(uuid, name, pipelineId, jobTypeId, coreJob, entryPointRecords, jsonSettings, createdBy))

    case UpdateJobState(jobId: Int, state: AnalysisJobStates.JobStates, message: String) =>
      pipeWith(dao.updateJobState(jobId, state, message))

    case GetEngineJobEntryPoints(jobId) => pipeWith(dao.getJobEntryPoints(jobId))

    // Need to consolidate this
    case UpdateJobStatus(uuid, state) =>
      pipeWith(dao.updateJobStateByUUID(uuid, state, s"Updating $uuid to $state"))

    // Testing/Debugging messages
    case "example-test-message" => respondWith("Successfully got example-test-message")

    case x => log.error(s"Unhandled message $x to database actor.")
  }
}

trait JobsDaoActorProvider {
  this: ActorRefFactoryProvider with JobsDaoProvider with SmrtLinkConfigProvider =>

  val jobsDaoActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[JobsDaoActor], jobsDao(), jobEngineConfig(), jobResolver()), "JobsDaoActor"))
}
