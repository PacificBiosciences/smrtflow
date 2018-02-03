package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{Actor, ActorRef, Props}
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.actors.EventManagerActor.CreateEvent
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobTypeIds
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jobtypes.PbsmrtpipeJobOptions
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
trait SmrtLinkEveMetricsProcessor {

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

  def extractPipelineId(sx: String): Option[String] =
    Try(sx.parseJson.convertTo[PbsmrtpipeJobOptions]).toOption
      .map(_.pipelineId)

  private def extractFromEntryPoints(dao: JobsDao,
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
      movieContexts <- extractFromEntryPoints(dao, entryPoints)
    } yield
      EngineJobMetrics(
        job.id,
        job.uuid,
        job.createdAt,
        job.updatedAt,
        job.state,
        job.jobTypeId,
        extractPipelineId(job.jsonSettings),
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
}

/**
  * List for comopleted Analysis jobs and process the output to convert to
  * SmrtLink Events that are sent to Event Manager. These events will
  * sent to Eve (if the system is configured to do so).
  *
  * @param dao  Jobs Dao
  * @param sendEveJobMetrics Enable or disable processing/converting of Completed Jobs
  * @param eventManagerActor Event Manager to send processed Events to
  */
class SmrtLinkEveMetricsProcessorActor(dao: JobsDao,
                                       sendEveJobMetrics: Boolean,
                                       eventManagerActor: ActorRef)
    extends Actor
    with SmrtLinkEveMetricsProcessor
    with LazyLogging {

  implicit val executionContext = context.dispatcher

  override def preStart() = {
    logger.info(s"Starting $self with sendEveJobMetrics=$sendEveJobMetrics")
  }

  override def receive: Receive = {
    case JobCompletedMessage(job) =>
      if (sendEveJobMetrics && (job.jobTypeId == JobTypeIds.PBSMRTPIPE.id)) {
        convertToEngineMetrics(dao, job.id)
          .map(em => convertToEvent(em)) onComplete {
          case Success(event) =>
            logger.info(s"Event $event")
            logger.debug(
              s"Successfully converted Job ${job.id} to EngineJobMetric with SmrtLinkEvent ${event.uuid}")
            eventManagerActor ! CreateEvent(event)
          case Failure(ex) =>
            logger.error(s"Failed to convert Job ${job.id} to EngineJobMetrics/SmrtLinkEvent Error:${ex.getMessage}")
        }
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
              eventManagerActor()))

  }
}
