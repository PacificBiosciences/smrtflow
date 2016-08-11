
package com.pacbio.secondaryinternal.services.jobtypes

import java.util.UUID
import java.io.{BufferedWriter, FileWriter}
import java.net.{URI, URL}
import java.nio.file.{Path, Paths}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask

import scala.util.{Failure, Success}
import spray._
import spray.routing._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._
import com.pacbio.common.actors.ActorSystemProvider
import com.pacbio.common.auth.AuthenticatorProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.LoggerFactoryProvider
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, CoreJob}
import com.pacbio.secondary.analysis.jobs.JobModels.{BoundEntryPoint, EngineJob, PipelineBaseOption, PipelineStrOption}
import com.pacbio.secondary.analysis.jobtypes.{ConvertImportFastaOptions, PbSmrtPipeJobOptions}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor.CreateJobType
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.{EngineJobEntryPoint, SmrtLinkJsonProtocols}
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondaryinternal.client.InternalAnalysisServiceClient
import com.pacbio.secondaryinternal.models._
import com.pacbio.secondaryinternal.{IOUtils, InternalAnalysisJsonProcotols, JobResolvers, SmrtLinkAnalysisInternalConfigProvider}
import com.typesafe.scalalogging.LazyLogging


class ConditionJobType(dbActor: ActorRef,
                       serviceStatusHost: String,
                       port: Int,
                       reseqConditionsDir: Path,
                       smrtLinkVersion: Option[String],
                       smrtLinkToolsVersion: Option[String])(implicit val actorSystem: ActorSystem)
  extends JobTypeService with LazyLogging{

  // import SecondaryAnalysisJsonProtocols._
  import SmrtLinkJsonProtocols.engineJobFormat
  import InternalAnalysisJsonProcotols._

  val description = "Create a multi-analysis job pipeline by running a pbsmrtipe Condition JSON driven Pipeline"
  val endpoint = "conditions"

  val PREFIX = "conditions"

  // This is the pipeline entry point defined
  val PIPELINE_ENTRY_POINT_ID = "cond_json"

  // There's some common code that needs to be pulled out
  val rootUpdateURL = new URL(s"http://$serviceStatusHost:$port/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX/jobs/pbsmrtpipe")

  def toUrl(host: String, port: Int) = new URL(s"http://$host:$port")

  val client = new InternalAnalysisServiceClient(toUrl(serviceStatusHost, port))

  def toURI(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  def toPbsmrtPipeJobOptions(pipelineId: String, conditionPath: Path, serviceURI: Option[URI]): PbSmrtPipeJobOptions = {

    val entryPoints = Seq(BoundEntryPoint(PIPELINE_ENTRY_POINT_ID, conditionPath.toString))

    // FIXME. this should be Option[Path] or Option[Map[String, String]]
    val envPath = ""
    PbSmrtPipeJobOptions(pipelineId, entryPoints, Seq.empty[PipelineBaseOption], Seq.empty[PipelineBaseOption], envPath, serviceURI)

  }

  val validateConditionRunRoute =
    path(PREFIX / "validate") {
      post {
        entity(as[ServiceConditionCsvPipeline]) { record =>
          complete {
            client.resolveConditionRecord(record)
          }
        }
      }
    }

  val helpRoute =
    path("status") {
      get {
        complete {
          "Condition Status"
        }
      }
    }

  val getJobsRoute =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobList(dbActor, endpoint)
          }
        }
      }
    }

  def resolvedJobConditionsTo(p: ResolvedConditionPipeline): ResolvedConditions = {
    ResolvedConditions(p.pipelineId, p.conditions.map(x => ResolvedCondition(x.id, FileTypes.DS_ALIGNMENTS.fileTypeId, Seq(x.path))))
  }

  // Note, all conditions jobs will be marked as standard pbsmrtpipe analysis jobs
  // This creates fundamental problems. If the UI was re-useable, we should
  // make this a specific page view
  val jobType = "pbsmrtpipe"

  //FIXME(mpkocher)(2016-4-19) make the path to the condition JSON files be configurable
  //FIXME(mpkocher)(2016-4-21) Need to address the EntryPoint, so the UI can display something meaningful
  val createJobRoute =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        post {
          entity(as[ServiceConditionCsvPipeline]) { record =>
            complete {
              for {
                uuid <- Future { UUID.randomUUID() }
                conditionPath <- Future { reseqConditionsDir.resolve(Paths.get(s"reseq-conditions-${uuid.toString}.json")) }
                reseqConditions <- client.resolveConditionRecord(record)
                _ <- Future { IOUtils.writeReseqConditions(reseqConditions, conditionPath) }
                coreJob <- Future { CoreJob(uuid, toPbsmrtPipeJobOptions(record.pipelineId, conditionPath, Option(toURI(rootUpdateURL, uuid)))) }
                engineJob <- (dbActor ?  CreateJobType(uuid, record.name, record.description, jobType, coreJob, None, record.toJson.toString, None, smrtLinkVersion, smrtLinkToolsVersion)).mapTo[EngineJob]
              } yield engineJob
            }
          }
        }
      }
    }

  val routes = helpRoute ~ sharedJobRoutes(dbActor) ~ validateConditionRunRoute ~ createJobRoute ~ getJobsRoute


}

trait ConditionJobTypeServiceProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with LoggerFactoryProvider
    with SmrtLinkConfigProvider
    with JobManagerServiceProvider
    with ActorSystemProvider
    with SmrtLinkAnalysisInternalConfigProvider =>

  val conditionJobTypeService: Singleton[ConditionJobType] =
    Singleton {() =>
      implicit val system = actorSystem()
      new ConditionJobType(
        jobsDaoActor(),
        if (host() != "0.0.0.0") host() else java.net.InetAddress.getLocalHost.getCanonicalHostName,
        port(), reseqConditions(), smrtLinkVersion(), smrtLinkToolsVersion()
      )
    }.bindToSet(JobTypes)

  //addService(conditionJobTypeService)
}
