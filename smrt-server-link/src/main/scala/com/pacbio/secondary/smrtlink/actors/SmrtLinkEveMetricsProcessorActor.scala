package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{Actor, ActorRef, Props}
import com.pacbio.common.models.CommonModels.{IdAble, IntIdAble}
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.actors.EventManagerActor.{
  CreateEvent,
  UploadTechSupportTgz
}
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobTypeIds
import com.pacbio.secondary.smrtlink.analysis.techsupport.TechSupportUtils
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import spray.json._

/**
  *
  * Processes Completed Analysis Jobs and converts them to SmrtLink Events that
  * can be sent to Eve.
  */
trait SmrtLinkEveMetricsProcessor extends DaoFutureUtils with LazyLogging {

  import SmrtLinkJsonProtocols._

  private val UNKNOWN_MOVIE_CONTEXT = "unknown"

  /**
    * Extract the movie context id.
    *
    * The services processing on import dataset will default to "unknown".
    * This value is useless and is removed.
    *
    * @param path Path to the SubreadSet
    * @return
    */
  private def extractMovieContextFromSubreadSet(path: Path): Set[String] = {
    DataSetLoader
      .loadSubreadSet(path)
      .getDataSetMetadata
      .getCollections
      .getCollectionMetadata
      .asScala
      .flatMap(ix => Option(ix.getContext))
      .filter(m => m.toLowerCase != UNKNOWN_MOVIE_CONTEXT)
      .toSet
  }
  private def orEmpty(movieContext: String): Set[String] = {
    if (movieContext.toLowerCase == UNKNOWN_MOVIE_CONTEXT) Set.empty[String]
    else Set(movieContext)
  }

  /**
    * In general, a SubreadSet has many movie context ids. In SL services
    * only the first Collection is processed and stored. Hence, we
    * attempt to get extract the data from the raw XML file which
    * contains the ground truth.
    *
    * If for any reason we can't extract the actual data from subreadset,
    * we return an empty movieContext from the db.
    *
    */
  private def fetchMovieContextsFromSubreadSet(dao: JobsDao, uuid: UUID)(
      implicit ec: ExecutionContext): Future[Set[String]] = {
    for {
      sset <- dao.getSubreadDataSetById(uuid)
      path <- Future.successful(Paths.get(sset.path))
    } yield
      Try(extractMovieContextFromSubreadSet(path))
        .getOrElse(orEmpty(sset.metadataContextId))
  }

  /**
    * Minimal parsing of JSON to extract the raw
    * pipelineId. This is done to have maximal
    * backwards compatibility.
    *
    */
  def extractPipelineIdFromJsonSettings(sx: String): Option[String] =
    sx.parseJson.asJsObject.getFields("pipelineId") match {
      case Seq(JsString(value)) => Some(value)
      case _ => None
    }

  /**
    * Extract all movie context values from all Entry points that are
    * of type SubreadSet.
    */
  private def extractMovieContextFromEntryPoints(
      dao: JobsDao,
      epoints: Seq[EngineJobEntryPoint])(
      implicit ec: ExecutionContext): Future[Set[String]] = {
    for {
      uuids <- Future.successful(
        epoints
          .filter(
            _.datasetType == DataSetMetaTypes.Subread.fileType.fileTypeId)
          .map(_.datasetUUID))
      movieContextIds <- Future.sequence(
        uuids.map(ix => fetchMovieContextsFromSubreadSet(dao, ix)))
    } yield movieContextIds.flatten.toSet
  }

  def convertToEngineMetrics(dao: JobsDao, jobIx: IdAble)(
      implicit ec: ExecutionContext): Future[EngineJobMetrics] = {
    for {
      job <- dao.getJobById(jobIx)
      entryPoints <- dao.getJobEntryPoints(job.id)
      movieContexts <- extractMovieContextFromEntryPoints(dao, entryPoints)
    } yield
      EngineJobMetrics(
        job.id,
        job.uuid,
        job.createdAt,
        job.updatedAt,
        job.state,
        job.jobTypeId,
        Try(extractPipelineIdFromJsonSettings(job.jsonSettings))
          .getOrElse(None),
        job.smrtlinkVersion,
        movieContexts,
        job.isActive,
        job.isMultiJob,
        job.importedAt
      )
  }

  def convertToEvent(engineJobMetrics: EngineJobMetrics): SmrtLinkEvent = {
    // If the EngineJobMetrics data model/schema changes,
    // The schema version should be incremented
    val schemaVersion = 1
    SmrtLinkEvent(EventTypes.JOB_METRICS,
                  schemaVersion,
                  UUID.randomUUID(),
                  JodaDateTime.now(),
                  engineJobMetrics.toJson.asJsObject)
  }

  /**
    *
    * Harvest all analysis jobs that are in a terminal state
    *
    */
  def harvestAnalysisJobs(dao: JobsDao, maxConcurrent: Int = 10)(
      implicit ec: ExecutionContext): Future[Seq[EngineJobMetrics]] = {
    def getIds(): Future[Seq[Int]] =
      dao
        .getJobsByTypeId(JobTypeIds.PBSMRTPIPE, includeInactive = true)
        .map(items => items.filter(_.state.isCompleted).map(job => job.id))

    def getConvertToEngineMetrics(i: Int): Future[EngineJobMetrics] =
      convertToEngineMetrics(dao, IntIdAble(i))

    for {
      ids <- getIds()
      engineJobMetrics <- runBatch(maxConcurrent,
                                   ids,
                                   getConvertToEngineMetrics)
    } yield engineJobMetrics
  }

  /**
    * Harvest all old jobs and write TS TGZ bundle **if non-empty job list**.
    *
    * This can be removed post 5.2
    */
  def harvestAnalysisJobsToTechSupportTgz(smrtLinkSystemId: UUID,
                                          user: String,
                                          smrtLinkVersion: Option[String],
                                          dnsName: Option[String],
                                          dao: JobsDao,
                                          maxConcurrent: Int = 10,
                                          outputTgz: Path)(
      implicit ec: ExecutionContext): Future[Seq[EngineJobMetrics]] = {
    harvestAnalysisJobs(dao, maxConcurrent).map { jobs =>
      if (jobs.isEmpty) {
        jobs
      } else {
        val comment = s"Auto Harvest Completed ${jobs.length} Analysis jobs"
        TechSupportUtils.writeSmrtLinkEveHistoryMetrics(smrtLinkSystemId,
                                                        user,
                                                        smrtLinkVersion,
                                                        dnsName,
                                                        Some(comment),
                                                        outputTgz,
                                                        jobs)
        jobs
      }
    }
  }

}

object SmrtLinkEveMetricsProcessActor {
  // This can be deleted after 5.2
  case class HarvestAnalysisJobs(user: String, smrtlinkVersion: String)
}

/**
  * List for completed Analysis jobs and process the output to convert to
  * SmrtLink Events that are sent to Event Manager. These events will
  * sent to Eve (if the system is configured to do so).
  *
  * @param dao               Jobs Dao
  * @param sendEveJobMetrics Initial configuration of enable or disable processing/converting of Completed Jobs
  * @param smrtLinkSystemId  SL System UUID (this can be removed post 5.2)
  * @param dnsName           DNS name of the system (this can be removed post 5.2)
  * @param eventManagerActor Event Manager to send processed Events to
  */
class SmrtLinkEveMetricsProcessorActor(dao: JobsDao,
                                       sendEveJobMetrics: Boolean,
                                       smrtLinkSystemId: UUID,
                                       dnsName: Option[String],
                                       eventManagerActor: ActorRef)
    extends Actor
    with SmrtLinkEveMetricsProcessor
    with LazyLogging {

  // This local state will be updated when the Eula changes
  private var sendJobMetrics = sendEveJobMetrics

  import SmrtLinkEveMetricsProcessActor._
  implicit val executionContext = context.dispatcher

  override def preStart() = {
    logger.info(s"Starting $self with sendJobMetrics=$sendJobMetrics")
  }

  override def receive: Receive = {
    case JobCompletedMessage(job) =>
      if (sendJobMetrics && (job.jobTypeId == JobTypeIds.PBSMRTPIPE.id) && job.state.isCompleted) {
        convertToEngineMetrics(dao, job.id)
          .map(em => convertToEvent(em)) onComplete {
          case Success(event) =>
            logger.info(s"Event $event")
            logger.debug(
              s"Successfully converted Job ${job.id} to EngineJobMetric with SmrtLinkEvent ${event.uuid}")
            eventManagerActor ! CreateEvent(event)
          case Failure(ex) =>
            logger.error(
              s"Failed to convert Job ${job.id} to EngineJobMetrics/SmrtLinkEvent Error:${ex.getMessage}")
        }
      }

    case HarvestAnalysisJobs(user, smrtLinkVersion) =>
      // This can be deleted after 5.2 release
      val tmpTgz = Files.createTempFile("harvested-jobs", "tgz")
      harvestAnalysisJobsToTechSupportTgz(smrtLinkSystemId,
                                          user,
                                          Some(smrtLinkVersion),
                                          dnsName,
                                          dao,
                                          10,
                                          tmpTgz) onComplete {
        case Success(engineJobMetrics) =>
          if (engineJobMetrics.isEmpty) {
            logger.info("No 'old' jobs to harvest. Skipping sending to Eve")
          } else {
            logger.info(
              s"Harvested ${engineJobMetrics.length} jobs to be sent to Eve")
            eventManagerActor ! UploadTechSupportTgz(tmpTgz)
          }
        case Failure(ex) =>
          logger.error(
            s"Failed to create Harvested Job History for ${ex.getMessage}")
          Files.deleteIfExists(tmpTgz)
      }

    case e: EulaRecord =>
      sendJobMetrics = e.enableJobMetrics
      logger.info(s"Updated sendJobMetrics=$sendJobMetrics")
      // This can be deleted after 5.2 release
      if (sendJobMetrics) {
        self ! HarvestAnalysisJobs
      }
    // case x => logger.warn(s"Unhandled message $x")
  }

}

trait SmrtLinkEveMetricsProcessActor {
  this: ActorRefFactoryProvider
    with SmrtLinkConfigProvider
    with EventManagerActorProvider
    with JobsDaoProvider =>

  lazy val sendEveJobMetrics = true

  val smrtLinkEvenMetricsProcessorActor: Singleton[ActorRef] = Singleton {
    () =>
      actorRefFactory().actorOf(
        Props(classOf[SmrtLinkEveMetricsProcessorActor],
              jobsDao(),
              sendEveJobMetrics,
              serverId(),
              dnsName(),
              eventManagerActor()))

  }
}
