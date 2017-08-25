package com.pacbio.secondary.smrtlink.actors

import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import akka.util.Timeout
import spray.json._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.ResourceNotFoundError
import CommonMessages._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{JobTask, JobTypeIds, RunnableJobWithId, UpdateJobTask, DataStoreFile}
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobtypes.DbBackUpJobOptions
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.database.DatabaseConfig
import com.pacbio.secondary.smrtlink.jobtypes.ServiceJobRunner
import com.pacbio.secondary.smrtlink.models.{EngineConfig, EngineJobEntryPointRecord, EulaRecord, DbBackUpServiceJobOptions}
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

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

  abstract class AdminMessage
}

object JobsDaoActor {
  import MessageTypes._

  // DataSet
  case object GetDataSetTypes extends DataSetMessage

  case class GetDataSetTypeById(i: String) extends DataSetMessage

  // Get all DataSet Metadata records
  case class GetDataSetMetaById(i: IdAble) extends DataSetMessage

  case class GetDataSetJobsByUUID(uuid: UUID) extends DataSetMessage

  case class DeleteDataSetById(id: IdAble) extends DataSetMessage

  case class UpdateDataSetByUUID(uuid: UUID, path: String, setIsActive: Boolean = true) extends DataSetMessage

  // DS Subreads
  case class GetSubreadDataSets(limit: Int, includeInactive: Boolean = false, projectIds: Seq[Int] = Nil) extends DataSetMessage

  case class GetSubreadDataSetById(i: IdAble) extends DataSetMessage

  case class GetSubreadDataSetDetailsById(i: IdAble) extends DataSetMessage

  // DS Reference
  case class GetReferenceDataSets(limit: Int, includeInactive: Boolean = false, projectIds: Seq[Int] = Nil) extends DataSetMessage

  case class GetReferenceDataSetById(i: IdAble) extends DataSetMessage

  case class GetReferenceDataSetDetailsById(i: IdAble) extends DataSetMessage

  // GMAP reference
  case class GetGmapReferenceDataSets(limit: Int, includeInactive: Boolean = false, projectIds: Seq[Int] = Nil) extends DataSetMessage

  case class GetGmapReferenceDataSetById(i: IdAble) extends DataSetMessage

  case class GetGmapReferenceDataSetDetailsById(i: IdAble) extends DataSetMessage

  // Alignment
  case class GetAlignmentDataSetById(i: IdAble) extends DataSetMessage

  case class GetAlignmentDataSets(limit: Int, includeInactive: Boolean = false, projectIds: Seq[Int] = Nil) extends DataSetMessage

  case class GetAlignmentDataSetDetailsById(i: IdAble) extends DataSetMessage

  // Hdf Subreads
  case class GetHdfSubreadDataSetById(i: IdAble) extends DataSetMessage

  case class GetHdfSubreadDataSetDetailsById(i: IdAble) extends DataSetMessage

  case class GetHdfSubreadDataSets(limit: Int, includeInactive: Boolean = false, projectIds: Seq[Int] = Nil) extends DataSetMessage

  // CCS reads
  case class GetConsensusReadDataSetById(i: IdAble) extends DataSetMessage

  case class GetConsensusReadDataSets(limit: Int, includeInactive: Boolean = false, projectIds: Seq[Int] = Nil) extends DataSetMessage

  case class GetConsensusReadDataSetDetailsById(i: IdAble) extends DataSetMessage

  // CCS alignments
  case class GetConsensusAlignmentDataSetById(i: IdAble) extends DataSetMessage

  case class GetConsensusAlignmentDataSets(limit: Int, includeInactive: Boolean = false, projectIds: Seq[Int] = Nil) extends DataSetMessage

  case class GetConsensusAlignmentDataSetDetailsById(i: IdAble) extends DataSetMessage

  // Barcode DataSets
  case class GetBarcodeDataSets(limit: Int, includeInactive: Boolean = false, projectIds: Seq[Int] = Nil) extends DataSetMessage

  case class GetBarcodeDataSetById(i: IdAble) extends DataSetMessage

  case class GetBarcodeDataSetDetailsById(i: IdAble) extends DataSetMessage

  // ContigSet
  case class GetContigDataSets(limit: Int, includeInactive: Boolean = false, projectId: Seq[Int] = Nil) extends DataSetMessage

  case class GetContigDataSetById(i: IdAble) extends DataSetMessage

  case class GetContigDataSetDetailsById(i: IdAble) extends DataSetMessage

  // DataStore Files
  case class GetDataStoreFileByUUID(uuid: UUID) extends DataStoreMessage

  case class UpdateDataStoreFile(uuid: UUID, setIsActive: Boolean = true, path: Option[String] = None, fileSize: Option[Long] = None) extends DataStoreMessage

  case class GetDataStoreServiceFilesByJobId(i: IdAble) extends DataStoreMessage

  case class GetDataStoreReportFilesByJobId(jobId: IdAble) extends DataStoreMessage

  case class GetDataStoreReportByUUID(uuid: UUID) extends DataStoreMessage

  case class GetDataStoreFiles(limit: Int = 1000, ignoreInactive: Boolean = true) extends DataStoreMessage

  case class GetDataStoreFilesByJobId(i: IdAble) extends DataStoreMessage

  case object GetEulas extends AdminMessage
  case class GetEulaByVersion(version: String) extends AdminMessage
  case class AddEulaRecord(eulaRecord: EulaRecord) extends AdminMessage
  case class DeleteEula(version: String) extends AdminMessage

  // Quartz Scheduled Messages
  case class SubmitDbBackUpJob(user: String, dbConfig: DatabaseConfig, rootBackUpDir: Path)


  // Projects Messages
  case class GetUserProjects(login: String) extends ProjectMessage
}

class JobsDaoActor(dao: JobsDao, val engineConfig: EngineConfig, val resolver: JobResourceResolver) extends PacBioActor with ActorLogging {

  import JobsDaoActor._
  import CommonModelImplicits._

  implicit val timeout = Timeout(5.second)

  override def preStart(): Unit = {
    log.info(s"Starting JobsDaoActor manager actor $self with $engineConfig")
  }

  override def preRestart(reason:Throwable, message:Option[Any]){
    super.preRestart(reason, message)
    log.error(s"$self (pre-restart) Unhandled exception ${reason.getMessage} Message $message")
  }


  def toMd5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.getBytes).map("%02x".format(_)).mkString

  def toE(msg: String) = throw new ResourceNotFoundError(msg)

  def receive: Receive = {

    case GetDataSetMetaById(i: IdAble) => pipeWith { dao.getDataSetById(i) }

    case GetDataSetJobsByUUID(i: UUID) => pipeWith(dao.getDataSetJobsByUUID(i))

    case DeleteDataSetById(id: IdAble) => pipeWith(dao.deleteDataSetById(id))

    case UpdateDataSetByUUID(uuid: UUID, path: String, setIsActive: Boolean) =>
      pipeWith(dao.updateDataSetById(uuid, path, setIsActive))

    // DataSet Types
    case GetDataSetTypes => pipeWith(dao.getDataSetTypes)

    case GetDataSetTypeById(n: String) => pipeWith { dao.getDataSetTypeById(n) }

    // Get Subreads
    case GetSubreadDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getSubreadDataSets(limit, includeInactive, projectIds))

    case GetSubreadDataSetById(n: IdAble) =>
      pipeWith {dao.getSubreadDataSetById(n) }

    case GetSubreadDataSetDetailsById(n) =>
      pipeWith { dao.getSubreadDataSetDetailsById(n)}

    // Get References
    case GetReferenceDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getReferenceDataSets(limit, includeInactive, projectIds))

    case GetReferenceDataSetById(i) =>
      pipeWith {dao.getReferenceDataSetById(i) }

    case GetReferenceDataSetDetailsById(id) =>
      pipeWith {dao.getReferenceDataSetDetailsById(id)}

    // Get GMAP References
    case GetGmapReferenceDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getGmapReferenceDataSets(limit, includeInactive, projectIds))

    case GetGmapReferenceDataSetById(id) =>
      pipeWith {dao.getGmapReferenceDataSetById(id)}

    case GetGmapReferenceDataSetDetailsById(i) =>
      pipeWith { dao.getGmapReferenceDataSetDetailsById(i)}

    // get Alignments
    case GetAlignmentDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getAlignmentDataSets(limit, includeInactive, projectIds))

    case GetAlignmentDataSetById(n) =>
      pipeWith { dao.getAlignmentDataSetById(n)}

    case GetAlignmentDataSetDetailsById(i) =>
      pipeWith {dao.getAlignmentDataSetDetailsById(i)}

    // Get HDF Subreads
    case GetHdfSubreadDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getHdfDataSets(limit, includeInactive, projectIds))

    case GetHdfSubreadDataSetById(n) =>
      pipeWith {dao.getHdfDataSetById(n) }

    case GetHdfSubreadDataSetDetailsById(n) =>
      pipeWith {dao.getHdfDataSetDetailsById(n)}

    // Get CCS Subreads
    case GetConsensusReadDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getConsensusReadDataSets(limit, includeInactive, projectIds))

    case GetConsensusReadDataSetById(n) =>
      pipeWith {dao.getConsensusReadDataSetById(n)}

    case GetConsensusReadDataSetDetailsById(i) =>
      pipeWith { dao.getConsensusReadDataSetDetailsById(i)}

    // Get CCS Subreads
    case GetConsensusAlignmentDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getConsensusAlignmentDataSets(limit, includeInactive, projectIds))

    case GetConsensusAlignmentDataSetById(n) =>
      pipeWith { dao.getConsensusAlignmentDataSetById(n)}

    case GetConsensusAlignmentDataSetDetailsById(i) =>
      pipeWith {dao.getConsensusAlignmentDataSetDetailsById(i) }

    // Get Barcodes
    case GetBarcodeDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getBarcodeDataSets(limit, includeInactive, projectIds))

    case GetBarcodeDataSetById(n) =>
      pipeWith {dao.getBarcodeDataSetById(n)}

    case GetBarcodeDataSetDetailsById(i) =>
      pipeWith {dao.getBarcodeDataSetDetailsById(i)}

    // Contigs
    case GetContigDataSets(limit: Int, includeInactive: Boolean, projectIds: Seq[Int]) =>
      pipeWith(dao.getContigDataSets(limit, includeInactive, projectIds))

    case GetContigDataSetById(n) =>
      pipeWith {dao.getContigDataSetById(n)}

    case GetContigDataSetDetailsById(i) =>
      pipeWith {dao.getContigDataSetDetailsById(i)}

    // DataStore Files
    case GetDataStoreFiles(limit: Int, ignoreInactive: Boolean) => pipeWith(dao.getDataStoreFiles(ignoreInactive))

    case GetDataStoreFileByUUID(uuid: UUID) =>
      pipeWith {dao.getDataStoreFileByUUID(uuid)}

    case GetDataStoreFilesByJobId(jobId) => pipeWith(dao.getDataStoreFilesByJobId(jobId))

    case GetDataStoreServiceFilesByJobId(jobId) => pipeWith(dao.getDataStoreServiceFilesByJobId(jobId))

    // Reports
    case GetDataStoreReportFilesByJobId(jobId) => pipeWith(dao.getDataStoreReportFilesByJobId(jobId))

    case GetDataStoreReportByUUID(reportUUID: UUID) => pipeWith(dao.getDataStoreReportByUUID(reportUUID))

    case ImportDataStoreFileByJobId(dsf: DataStoreFile, jobId) => pipeWith(dao.insertDataStoreFileById(dsf, jobId))

    case GetUserProjects(login) => pipeWith(dao.getUserProjects(login))

    case SubmitDbBackUpJob(user: String, dbConfig: DatabaseConfig, rootBackUpDir: Path) => {
      // The use of "user" here is perhaps misleading. This is not the wso2 user "id". This duplication
      // is generated because of the raw/naked service calls to SL backend that don't go through wso2
      // and hence, don't use the JWT to encode the user metadata.
      val uuid = UUID.randomUUID()
      val name = s"Automated DB BackUp Job by $user"
      val comment = s"Quartz Scheduled Automated DB BackUp Job by $user"

      val opts = DbBackUpServiceJobOptions(user, comment)

      val jobOpts = DbBackUpJobOptions(rootBackUpDir,
        dbName = dbConfig.dbName,
        dbUser = dbConfig.username,
        dbPort = dbConfig.port,
        dbPassword = dbConfig.password)

      val coreJob = CoreJob(uuid, jobOpts)
      //val cJob = CreateJobType(uuid, name, comment, JobTypeIds.DB_BACKUP.id, coreJob, None, opts.toJson.toString(), Some(user), None, None)
      //log.info(s"Automated DB backup request created $cJob")
      //self ! cJob

    }


    case x => log.warning(s"Unhandled message $x to database actor.")
  }
}

trait JobsDaoActorProvider {
  this: ActorRefFactoryProvider with JobsDaoProvider with SmrtLinkConfigProvider =>

  val jobsDaoActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[JobsDaoActor], jobsDao(), jobEngineConfig(), jobResolver()), "JobsDaoActor"))
}
