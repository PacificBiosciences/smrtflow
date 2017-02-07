package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Path, Paths}
import java.security.MessageDigest
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import akka.util.Timeout
import com.pacbio.common.actors.{ActorRefFactoryProvider, PacBioActor}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.CommonModels.IdAble
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
import com.pacbio.secondary.smrtlink.models.{Converters, EngineJobEntryPointRecord, GmapReferenceServiceDataSet, ProjectRequest, ReferenceServiceDataSet}
import org.joda.time.{DateTime => JodaDateTime}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object MessageTypes {
  abstract class ProjectMessage

  abstract class JobMessage

  abstract class DataSetMessage

  abstract class DataStoreMessage

  abstract class AdminMessage
}

object JobsDaoActor {
  import MessageTypes._

  // All Job Message Protocols
  case object GetAllJobs extends JobMessage

  case class GetJobByIdAble(ix: IdAble) extends JobMessage

  case class GetJobsByJobType(jobTypeId: String, includeInactive: Boolean = false) extends JobMessage

  case class GetJobEventsByJobId(jobId: Int) extends JobMessage

  case class GetJobTasks(jobId: IdAble) extends JobMessage

  case class GetJobTask(taskId: UUID) extends JobMessage

  case class UpdateJobState(jobId: Int, state: AnalysisJobStates.JobStates, message: String) extends JobMessage

  // createdAt is when the task was created, not when the Service has
  // created the record. This is attempting to defer to the layer that
  // has defined how tasks are created and not to create duplicate, and
  // potentially inconsistent state (i.e., the database has createdAt that
  // is different from the pbmsrtpipe createdAt datetime)
  case class CreateJobTask(uuid: UUID,
                           jobId: IdAble,
                           taskId: String,
                           taskTypeId: String,
                           name: String,
                           createdAt: JodaDateTime) extends JobMessage

  // Update a Job Task status
  case class UpdateJobTaskStatus(uuid: UUID, jobId: Int, state: String, message: String, errorMessage: Option[String])

  case class CreateJobType(
      uuid: UUID,
      name: String,
      comment: String,
      jobTypeId: String,
      coreJob: CoreJob,
      engineEntryPoints: Option[Seq[EngineJobEntryPointRecord]] = None,
      jsonSettings: String,
      createdBy: Option[String],
      smrtLinkVersion: Option[String],
      smrtLinkToolsVersion: Option[String]) extends JobMessage

  case class GetJobChildrenByUUID(jobId: UUID) extends JobMessage
  case class GetJobChildrenById(jobId: Int) extends JobMessage
  case class DeleteJobByUUID(jobId: UUID) extends JobMessage

  // Get all DataSet Entry Points
  case class GetEngineJobEntryPoints(jobId: Int) extends JobMessage

  // DataSet
  case object GetDataSetTypes extends DataSetMessage

  case class GetDataSetTypeById(i: String) extends DataSetMessage

  // Get all DataSet Metadata records
  case class GetDataSetMetaById(i: Int) extends DataSetMessage

  case class GetDataSetMetaByUUID(uuid: UUID) extends DataSetMessage

  case class GetDataSetJobsByUUID(uuid: UUID) extends DataSetMessage

  case class DeleteDataSetById(id: Int) extends DataSetMessage
  case class DeleteDataSetByUUID(uuid: UUID) extends DataSetMessage

  // DS Subreads
  case class GetSubreadDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  case class GetSubreadDataSetById(i: Int) extends DataSetMessage

  case class GetSubreadDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetSubreadDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetSubreadDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // DS Reference
  case class GetReferenceDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  case class GetReferenceDataSetById(i: Int) extends DataSetMessage

  case class GetReferenceDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetReferenceDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetReferenceDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // GMAP reference
  case class GetGmapReferenceDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  case class GetGmapReferenceDataSetById(i: Int) extends DataSetMessage

  case class GetGmapReferenceDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetGmapReferenceDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetGmapReferenceDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // Alignment
  case class GetAlignmentDataSetById(i: Int) extends DataSetMessage

  case class GetAlignmentDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetAlignmentDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  case class GetAlignmentDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetAlignmentDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // Hdf Subreads
  case class GetHdfSubreadDataSetById(i: Int) extends DataSetMessage

  case class GetHdfSubreadDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetHdfSubreadDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetHdfSubreadDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  case class GetHdfSubreadDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  // CCS reads
  case class GetConsensusReadDataSetById(i: Int) extends DataSetMessage

  case class GetConsensusReadDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetConsensusReadDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  case class GetConsensusReadDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetConsensusReadDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // CCS alignments
  case class GetConsensusAlignmentDataSetById(i: Int) extends DataSetMessage

  case class GetConsensusAlignmentDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetConsensusAlignmentDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  case class GetConsensusAlignmentDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetConsensusAlignmentDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // Barcode DataSets
  case class GetBarcodeDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  case class GetBarcodeDataSetById(i: Int) extends DataSetMessage

  case class GetBarcodeDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetBarcodeDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetBarcodeDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // ContigSet
  case class GetContigDataSets(limit: Int, includeInactive: Boolean = false, projectId: Option[Int] = None) extends DataSetMessage

  case class GetContigDataSetById(i: Int) extends DataSetMessage

  case class GetContigDataSetByUUID(uuid: UUID) extends DataSetMessage

  case class GetContigDataSetDetailsById(i: Int) extends DataSetMessage

  case class GetContigDataSetDetailsByUUID(uuid: UUID) extends DataSetMessage

  // Import a Reference Dataset
  case class ImportReferenceDataSet(ds: ReferenceServiceDataSet) extends DataSetMessage

  // Import a GMAP reference
  case class ImportGmapReferenceDataSet(ds: GmapReferenceServiceDataSet) extends DataSetMessage

  // This should probably be a different actor
  // Convert a Reference to a Dataset and Import
  case class ConvertReferenceInfoToDataset(path: String, dsPath: Path) extends DataSetMessage

  case class ImportSubreadDataSetFromFile(path: String) extends DataSetMessage


  // DataStore Files
  case class GetDataStoreFileByUUID(uuid: UUID) extends DataStoreMessage

  case class GetDataStoreServiceFilesByJobId(i: Int) extends DataStoreMessage
  case class GetDataStoreServiceFilesByJobUuid(uuid: UUID) extends DataStoreMessage

  case class GetDataStoreReportFilesByJobId(jobId: Int) extends DataStoreMessage
  case class GetDataStoreReportFilesByJobUuid(jobUuid: UUID) extends DataStoreMessage

  case class GetDataStoreReportByUUID(uuid: UUID) extends DataStoreMessage

  case class GetDataStoreFiles(limit: Int = 1000, ignoreInactive: Boolean = true) extends DataStoreMessage

  case class GetDataStoreFilesByJobId(i: Int) extends DataStoreMessage

  case class GetDataStoreFilesByJobUUID(i: UUID) extends DataStoreMessage

  case object GetEulas extends AdminMessage
  case class GetEulaByVersion(version: String) extends AdminMessage
  case class AcceptEula(user: String, smrtlinkVersion: String, enableInstallMetrics: Boolean, enableJobMetrics: Boolean) extends AdminMessage
  case class DeleteEula(version: String) extends AdminMessage
}

class JobsDaoActor(dao: JobsDao, val engineConfig: EngineConfig, val resolver: JobResourceResolver) extends PacBioActor with EngineActorCore with ActorLogging {

  import JobsDaoActor._

  final val QUICK_TASK_IDS = Set("import_dataset", "merge_dataset", "mock-pbsmrtpipe").map(JobTypeId)

  implicit val timeout = Timeout(5.second)

  val logStatusInterval = if (engineConfig.debugMode) 1.minute else 10.minutes

  //MK Probably want to have better model for this
  val checkForWorkInterval = 5.seconds

  val checkForWorkTick = context.system.scheduler.schedule(5.seconds, checkForWorkInterval, self, CheckForRunnableJob)

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
          val rjob = RunJob(runnableJobWithId.job, outputDir)
          log.info(s"Sending worker $worker job (type:${rjob.job.jobOptions.toJob.jobTypeId}) $rjob")
          worker ! rjob
        case Failure(ex) =>
          val emsg = s"addJobToWorker Unable to update state ${runnableJobWithId.job.uuid} Marking as Failed. Error ${ex.getMessage}"
          log.error(emsg)
          self ! UpdateJobStatus(runnableJobWithId.job.uuid, AnalysisJobStates.FAILED, Some(emsg))
      }
    }
  }

  // This should return a future
  def checkForWork(): Unit = {
    //log.info(s"Checking for work. # of Workers ${workers.size} # Quick Workers ${quickWorkers.size} ")

    if (workers.nonEmpty) {
      val f = dao.getNextRunnableJobWithId

      f onSuccess {
        case Right(runnableJob) => addJobToWorker(runnableJob, workers)
        case Left(e) => log.debug(s"No work found. ${e.message}")
      }

      f onFailure {
        case e => log.error(s"Failure checking for new work ${e.getMessage}")
      }
    } else {
      log.debug("No available workers.")
    }
    if (quickWorkers.nonEmpty) {
      val f = dao.getNextRunnableQuickJobWithId
      f onSuccess {
        case Right(runnableJob) => addJobToWorker(runnableJob, quickWorkers)
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

      val errorMessage = result.state match {
        case AnalysisJobStates.FAILED => Some(result.message)
        case _ => None
      }

      val f = dao.updateJobStateByUUID(result.uuid, result.state, s"Updated to ${result.state}", errorMessage)

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
        dao.getDataStoreFiles(true).map { dsJobFiles =>
          log.debug(s"Number of active DataStore files ${dsJobFiles.size}")
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
        case Right(m) => Future { MessageResponse(m.message) }
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

    case GetAllJobs => pipeWith(dao.getJobs(1000))

    case GetJobsByJobType(jobTypeId, includeInactive: Boolean) => pipeWith(dao.getJobsByTypeId(jobTypeId, includeInactive))

    case GetJobByIdAble(ix) => pipeWith { dao.getJobByIdAble(ix) }

    case GetJobEventsByJobId(jobId: Int) => pipeWith(dao.getJobEventsByJobId(jobId))

    // Job Task related message
    case CreateJobTask(uuid, jobId, taskId, taskTypeId, name, createdAt) => pipeWith {
      for {
        job <- dao.getJobByIdAble(jobId)
        jobTask <- dao.addJobTask(JobTask(uuid, job.id, taskId, taskTypeId, name, AnalysisJobStates.CREATED.toString, createdAt, createdAt, None))
      } yield jobTask
    }

    case UpdateJobTaskStatus(taskUUID, jobId, state, message, errorMessage) =>
      pipeWith {dao.updateJobTask(UpdateJobTask(jobId, taskUUID, state, message, errorMessage))}

    case GetJobChildrenByUUID(jobId: UUID) => dao.getJobChildrenByUUID(jobId) pipeTo sender
    case GetJobChildrenById(jobId: Int) => dao.getJobChildrenById(jobId) pipeTo sender

    // This needs to be refactored and/or pushed into the Dao
    case DeleteJobByUUID(jobId: UUID) => pipeWith {
      dao.deleteJobByUUID(jobId).map { job =>
        dao.getDataStoreFilesByJobUUID(job.uuid).map { dss =>
          dss.map(ds => dao.deleteDataStoreJobFile(ds.dataStoreFile.uniqueId))
        }
        job
      }
    }

    case GetJobTasks(ix: IdAble) => pipeWith { dao.getJobTasks(ix) }

    case GetJobTask(taskId: UUID) => pipeWith { dao.getJobTask(taskId) }

    case DeleteDataStoreFile(uuid: UUID) => dao.deleteDataStoreJobFile(uuid) pipeTo sender

    case GetDataSetMetaById(i: Int) => pipeWith { dao.getDataSetById(i) }

    case GetDataSetMetaByUUID(i: UUID) => pipeWith { dao.getDataSetByUUID(i) }

    case GetDataSetJobsByUUID(i: UUID) => pipeWith(dao.getDataSetJobsByUUID(i))

    case DeleteDataSetById(id: Int) => pipeWith(dao.deleteDataSetById(id))
    case DeleteDataSetByUUID(uuid: UUID) => pipeWith(dao.deleteDataSetByUUID(uuid))

    // DataSet Types
    case GetDataSetTypes => pipeWith(dao.getDataSetTypes)

    case GetDataSetTypeById(n: String) => pipeWith { dao.getDataSetTypeById(n) }

    // Get Subreads
    case GetSubreadDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getSubreadDataSets(limit, includeInactive, projectId))

    case GetSubreadDataSetById(n: Int) =>
      pipeWith {dao.getSubreadDataSetById(n) }

    case GetSubreadDataSetByUUID(uuid: UUID) =>
      pipeWith {dao.getSubreadDataSetByUUID(uuid)}

    case GetSubreadDataSetDetailsById(n: Int) =>
      pipeWith { dao.getSubreadDataSetDetailsById(n)}

    case GetSubreadDataSetDetailsByUUID(uuid: UUID) =>
      pipeWith {dao.getSubreadDataSetDetailsByUUID(uuid)}

    // Get References
    case GetReferenceDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getReferenceDataSets(limit, includeInactive, projectId))

    case GetReferenceDataSetById(id: Int) =>
      pipeWith {dao.getReferenceDataSetById(id) }

    case GetReferenceDataSetByUUID(uuid: UUID) =>
      pipeWith {dao.getReferenceDataSetByUUID(uuid)}

    case GetReferenceDataSetDetailsById(id: Int) =>
      pipeWith {dao.getReferenceDataSetDetailsById(id)}

    case GetReferenceDataSetDetailsByUUID(id: UUID) =>
      pipeWith {dao.getReferenceDataSetDetailsByUUID(id)}

    // Get GMAP References
    case GetGmapReferenceDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getGmapReferenceDataSets(limit, includeInactive, projectId))

    case GetGmapReferenceDataSetById(id: Int) =>
      pipeWith {dao.getGmapReferenceDataSetById(id)}

    case GetGmapReferenceDataSetByUUID(uuid: UUID) =>
      pipeWith { dao.getGmapReferenceDataSetByUUID(uuid) }

    case GetGmapReferenceDataSetDetailsById(id: Int) =>
      pipeWith { dao.getGmapReferenceDataSetDetailsById(id)}

    case GetGmapReferenceDataSetDetailsByUUID(id: UUID) =>
      pipeWith {dao.getGmapReferenceDataSetDetailsByUUID(id)}

    case ImportReferenceDataSet(ds: ReferenceServiceDataSet) => pipeWith {
      log.debug("inserting reference dataset")
      dao.insertReferenceDataSet(ds)
    }

    case ImportGmapReferenceDataSet(ds: GmapReferenceServiceDataSet) => pipeWith {
      log.debug("inserting reference dataset")
      dao.insertGmapReferenceDataSet(ds)
    }

    // get Alignments
    case GetAlignmentDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getAlignmentDataSets(limit, includeInactive, projectId))

    case GetAlignmentDataSetById(n: Int) =>
      pipeWith { dao.getAlignmentDataSetById(n)}

    case GetAlignmentDataSetByUUID(uuid: UUID) =>
      pipeWith {dao.getAlignmentDataSetByUUID(uuid)}

    case GetAlignmentDataSetDetailsByUUID(uuid) =>
      pipeWith {dao.getAlignmentDataSetDetailsByUUID(uuid)}

    case GetAlignmentDataSetDetailsById(i) =>
      pipeWith {dao.getAlignmentDataSetDetailsById(i)}

    // Get HDF Subreads
    case GetHdfSubreadDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getHdfDataSets(limit, includeInactive, projectId))

    case GetHdfSubreadDataSetById(n: Int) =>
      pipeWith {dao.getHdfDataSetById(n) }

    case GetHdfSubreadDataSetByUUID(uuid: UUID) =>
      pipeWith { dao.getHdfDataSetByUUID(uuid)}

    case GetHdfSubreadDataSetDetailsById(n: Int) =>
      pipeWith {dao.getHdfDataSetDetailsById(n)}

    case GetHdfSubreadDataSetDetailsByUUID(uuid: UUID) =>
      pipeWith {dao.getHdfDataSetDetailsByUUID(uuid)}

    // Get CCS Subreads
    case GetConsensusReadDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getConsensusReadDataSets(limit, includeInactive, projectId))

    case GetConsensusReadDataSetById(n: Int) =>
      pipeWith {dao.getConsensusReadDataSetById(n)}

    case GetConsensusReadDataSetByUUID(uuid: UUID) =>
      pipeWith {dao.getConsensusReadDataSetByUUID(uuid) }

    case GetConsensusReadDataSetDetailsByUUID(uuid) =>
      pipeWith { dao.getConsensusReadDataSetDetailsByUUID(uuid) }

    case GetConsensusReadDataSetDetailsById(i) =>
      pipeWith { dao.getConsensusReadDataSetDetailsById(i)}

    // Get CCS Subreads
    case GetConsensusAlignmentDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getConsensusAlignmentDataSets(limit, includeInactive, projectId))

    case GetConsensusAlignmentDataSetById(n: Int) =>
      pipeWith { dao.getConsensusAlignmentDataSetById(n)}

    case GetConsensusAlignmentDataSetByUUID(uuid: UUID) =>
      pipeWith { dao.getConsensusAlignmentDataSetByUUID(uuid)}

    case GetConsensusAlignmentDataSetDetailsByUUID(uuid) =>
      pipeWith {dao.getConsensusAlignmentDataSetDetailsByUUID(uuid)}

    case GetConsensusAlignmentDataSetDetailsById(i) =>
      pipeWith {dao.getConsensusAlignmentDataSetDetailsById(i) }

    // Get Barcodes
    case GetBarcodeDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getBarcodeDataSets(limit, includeInactive, projectId))

    case GetBarcodeDataSetById(n: Int) =>
      pipeWith {dao.getBarcodeDataSetById(n)}

    case GetBarcodeDataSetByUUID(uuid: UUID) =>
      pipeWith {dao.getBarcodeDataSetByUUID(uuid)}

    case GetBarcodeDataSetDetailsByUUID(uuid) =>
      pipeWith {dao.getBarcodeDataSetDetailsByUUID(uuid)}

    case GetBarcodeDataSetDetailsById(i) =>
      pipeWith {dao.getBarcodeDataSetDetailsById(i)}

    // Contigs
    case GetContigDataSets(limit: Int, includeInactive: Boolean, projectId: Option[Int]) =>
      pipeWith(dao.getContigDataSets(limit, includeInactive, projectId))

    case GetContigDataSetById(n: Int) =>
      pipeWith {dao.getContigDataSetById(n)}

    case GetContigDataSetByUUID(uuid: UUID) =>
      pipeWith {dao.getContigDataSetByUUID(uuid)}

    case GetContigDataSetDetailsByUUID(uuid) =>
      pipeWith {dao.getContigDataSetDetailsByUUID(uuid)}

    case GetContigDataSetDetailsById(i) =>
      pipeWith {dao.getContigDataSetDetailsById(i)}

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
    case GetDataStoreFiles(limit: Int, ignoreInactive: Boolean) => pipeWith(dao.getDataStoreFiles2(ignoreInactive))

    case GetDataStoreFileByUUID(uuid: UUID) =>
      pipeWith {dao.getDataStoreFileByUUID2(uuid)}

    case GetDataStoreFilesByJobId(jobId) => pipeWith(dao.getDataStoreFilesByJobId(jobId))

    case GetDataStoreServiceFilesByJobId(jobId: Int) => pipeWith(dao.getDataStoreServiceFilesByJobId(jobId))
    case GetDataStoreServiceFilesByJobUuid(jobUuid: UUID) => pipeWith(dao.getDataStoreServiceFilesByJobUuid(jobUuid))

    // Reports
    case GetDataStoreReportFilesByJobId(jobId: Int) => pipeWith(dao.getDataStoreReportFilesByJobId(jobId))
    case GetDataStoreReportFilesByJobUuid(jobUuid: UUID) => pipeWith(dao.getDataStoreReportFilesByJobUuid(jobUuid))

    case GetDataStoreReportByUUID(reportUUID: UUID) => pipeWith {
      dao.getDataStoreReportByUUID(reportUUID).map(_.getOrElse(toE(s"Unable to find report ${reportUUID.toString}")))
    }

    case GetDataStoreFilesByJobUUID(id) => pipeWith(dao.getDataStoreFilesByJobUUID(id))

    case ImportDataStoreFileByJobId(dsf: DataStoreFile, jobId) => pipeWith(dao.insertDataStoreFileById(dsf, jobId))

    case CreateJobType(uuid, name, pipelineId, jobTypeId, coreJob, entryPointRecords, jsonSettings, createdBy, smrtLinkVersion, smrtLinkToolsVersion) =>
      val fx = dao.createJob(uuid, name, pipelineId, jobTypeId, coreJob, entryPointRecords, jsonSettings, createdBy, smrtLinkVersion, smrtLinkToolsVersion)
      fx onSuccess { case _ => self ! CheckForRunnableJob}
      //fx onFailure { case ex => log.error(s"Failed creating job uuid:$uuid name:$name ${ex.getMessage}")}
      pipeWith(fx)

    case UpdateJobState(jobId: Int, state: AnalysisJobStates.JobStates, message: String) =>
      pipeWith(dao.updateJobState(jobId, state, message))

    case GetEngineJobEntryPoints(jobId) => pipeWith(dao.getJobEntryPoints(jobId))

    // Need to consolidate this
    case UpdateJobStatus(uuid, state, errorMessage) =>
      pipeWith(dao.updateJobStateByUUID(uuid, state, s"Updating $uuid to $state", errorMessage))

    case GetEulas => pipeWith(dao.getEulas)

    case GetEulaByVersion(version) =>
      pipeWith {dao.getEulaByVersion(version) }

    case AcceptEula(user, smrtlinkVersion, enableInstallMetrics, enableJobMetrics) =>
      pipeWith(dao.addEulaAcceptance(user, smrtlinkVersion, enableInstallMetrics, enableJobMetrics))

    case DeleteEula(version) => pipeWith {
      dao.removeEula(version).map(x =>
        if (x == 0) SuccessMessage(s"No user agreement for version $version was found")
        else SuccessMessage(s"Removed user agreement for version $version")
      )
    }

    case x => log.warning(s"Unhandled message $x to database actor.")
  }
}

trait JobsDaoActorProvider {
  this: ActorRefFactoryProvider with JobsDaoProvider with SmrtLinkConfigProvider =>

  val jobsDaoActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[JobsDaoActor], jobsDao(), jobEngineConfig(), jobResolver()), "JobsDaoActor"))
}
