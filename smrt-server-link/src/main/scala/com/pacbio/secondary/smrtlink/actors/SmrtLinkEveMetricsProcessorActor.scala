package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{Actor, ActorRef, Props}
import com.pacbio.common.models.CommonModels.{IdAble, IntIdAble}
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.common.models.{JodaDateTimeProtocol, UUIDJsonProtocol}
import com.pacbio.secondary.smrtlink.actors.EventManagerActor.{
  CreateEvent,
  UploadTechSupportTgz
}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobStatesJsonProtocol
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobConstants,
  JobTypeIds
}
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.analysis.techsupport.TechSupportUtils
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils

import scala.concurrent.{ExecutionContext, Future, blocking}
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

  // This is a workaround for changing the raw jsonSettings (str) to proper JsObject
  // to be used in elastic search. This should be resolved by SE-1227
  private def workaroundForJsonSettings(jsObject: JsObject,
                                        job: EngineJob): JsObject = {

    import DefaultJsonProtocol._

    val jsonSettings: JsObject = job.jsonSettings.parseJson.asJsObject()
    val update = JsObject("jsonSettings" -> jsonSettings)

    val jsJob: JsObject = job.toJson.asJsObject

    val jsUpdatedJob = new JsObject(jsJob.fields ++ update.fields)

    val jobUpdate = JsObject("job" -> jsUpdatedJob)
    new JsObject(jsObject.fields ++ jobUpdate.fields)
  }

  def convertToEngineJobMetricsEvent(
      engineJobMetrics: EngineJobMetrics): SmrtLinkEvent =
    SmrtLinkEvent.from(EventTypes.JOB_METRICS,
                       engineJobMetrics.toJson.asJsObject)

  def convertToCompletedJobEvent(
      completedJob: CompletedEngineJob): SmrtLinkEvent = {
    val rawJsObject = completedJob.toJson.asJsObject
    val jsObject =
      Try(workaroundForJsonSettings(rawJsObject, completedJob.job))
        .getOrElse(rawJsObject)

    SmrtLinkEvent.from(EventTypes.JOB_METRICS_INTERNAL,
                       jsObject,
                       eventTypeVersion = 2)
  }

  /**
    *
    * Harvest all analysis jobs that are in a terminal state
    *
    */
  def harvestAnalysisJobs(dao: JobsDao, maxConcurrent: Int)(
      implicit ec: ExecutionContext): Future[Seq[EngineJobMetrics]] = {
    def getIds(): Future[Seq[Int]] = {
      val c = JobSearchCriteria.allAnalysisJobs
      dao
        .getJobs(c)
        .map(items => items.filter(_.state.isCompleted).map(job => job.id))
    }

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

  private def fetchEntryPoint(dao: JobsDao, e: EngineJobEntryPoint)(
      implicit ec: ExecutionContext)
    : Future[CompletedBoundServiceEntryPoint] = {
    for {
      dsMetaData <- dao.getDataSetMetaData(e.datasetUUID)
    } yield
      CompletedBoundServiceEntryPoint(e.datasetType,
                                      dsMetaData.id,
                                      dsMetaData.uuid,
                                      dsMetaData.numRecords,
                                      dsMetaData.totalLength)
  }

  /**
    * Load a Report from file system. We strip out the tables and plot groups
    * to make the payload smaller. We are only interested in the
    * report attributes.
    */
  private def fetchReport(dao: JobsDao, path: Path)(
      implicit ec: ExecutionContext): Future[Report] = Future {
    blocking {
      ReportUtils.loadReport(path).copy(plotGroups = Nil, tables = Nil)
    }
  }

  def createCompletedJob(dao: JobsDao, jobId: Int)(
      implicit ec: ExecutionContext): Future[CompletedEngineJob] = {

    val fetchEntryPointFromDao = fetchEntryPoint(dao, _: EngineJobEntryPoint)
    val fetchReportFromDao = fetchReport(dao, _: Path)

    for {
      job <- dao.getJobById(jobId)
      datastoreFiles <- dao.getDataStoreFilesByJobId(job.id)
      entryPoints <- dao.getJobEntryPoints(job.id)
      completedEntryPoints <- Future.traverse(entryPoints)(
        fetchEntryPointFromDao)
      events <- dao.getJobEventsByJobId(job.id)
      dsReportFiles <- Future.successful(
        datastoreFiles.filter(
          _.dataStoreFile.fileTypeId == FileTypes.REPORT.fileTypeId))
      reports <- Future.traverse(dsReportFiles.map(x =>
        Paths.get(x.dataStoreFile.path)))(fetchReportFromDao)
    } yield CompletedEngineJob(job, completedEntryPoints, reports, events)

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
  * @param dao                   Jobs Dao
  * @param sendEveJobMetrics     Initial configuration of enable or disable processing/converting of Completed Jobs
  * @param enableInternalMetrics Send Internal Metrics to Eve
  * @param smrtLinkSystemId      SL System UUID (this can be removed post 5.2)
  * @param dnsName               DNS name of the system (this can be removed post 5.2)
  * @param eventManagerActor     Event Manager to send processed Events to
  */
class SmrtLinkEveMetricsProcessorActor(dao: JobsDao,
                                       sendEveJobMetrics: Boolean,
                                       enableInternalMetrics: Boolean,
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
    logger.info(
      s"Starting $self with sendJobMetrics=$sendJobMetrics and enableInternalMetrics=$enableInternalMetrics")
  }

  private def convertAndSend(job: EngineJob,
                             fx: => Future[SmrtLinkEvent]): Unit = {
    fx onComplete {
      case Success(event) =>
        logger.debug(s"Event $event")
        logger.info(
          s"Successfully converted Job id:${job.id} type:${job.jobTypeId} to SmrtLinkEvent eventTypeId:${event.eventTypeId} uuid:${event.uuid}")
        eventManagerActor ! CreateEvent(event)
      case Failure(ex) =>
        logger.error(
          s"Failed to convert Job id:${job.id} type:${job.jobTypeId} to SmrtLinkEvent Error:${ex.getMessage}")
    }
  }

  override def receive: Receive = {
    case JobChangeStateMessage(job) =>
      if (job.state.isCompleted) {

        // Customer facing Metrics
        if (sendJobMetrics && (job.jobTypeId == JobTypeIds.PBSMRTPIPE.id)) {
          convertAndSend(job,
                         convertToEngineMetrics(dao, job.id)
                           .map(convertToEngineJobMetricsEvent))
        }

        // Internal Metrics
        if (enableInternalMetrics) {
          convertAndSend(
            job,
            createCompletedJob(dao, job.id).map(convertToCompletedJobEvent))
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
            FileUtils.deleteQuietly(tmpTgz.toFile)
          } else {
            logger.info(
              s"Harvested ${engineJobMetrics.length} jobs to be sent to Eve")
            eventManagerActor ! UploadTechSupportTgz(tmpTgz)
          }
        case Failure(ex) =>
          logger.error(
            s"Failed to create Harvested Job History for ${ex.getMessage}")
          FileUtils.deleteQuietly(tmpTgz.toFile)
      }

    case e: EulaRecord =>
      sendJobMetrics = e.enableJobMetrics
      logger.info(
        s"Updated sendJobMetrics=$sendJobMetrics (with enableInternalMetrics=$enableInternalMetrics)")
      // This can be deleted after 5.2 release
      if (sendJobMetrics) {
        self ! HarvestAnalysisJobs(e.user, e.smrtlinkVersion)
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
              enableInternalMetrics(),
              serverId(),
              dnsName(),
              eventManagerActor()))

  }
}
