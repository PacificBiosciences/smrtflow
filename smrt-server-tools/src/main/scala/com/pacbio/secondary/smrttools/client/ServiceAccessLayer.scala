// derived from PrimaryClient.scala in PAWS
package com.pacbio.secondary.smrttools.client

import com.pacbio.secondary.analysis.constants.{GlobalConstants, FileTypes}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.common.models._

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.xml.XML

import java.net.URL
import java.util.UUID

//FIXME(mkocher)(2016-2-2): This needs to be centralized.
object SmrtLinkServicesModels {

  case class CreateDataSet(path: String, datasetType: String)

}

object ServicesClientJsonProtocol extends SmrtLinkJsonProtocols

/**
 * Client to Primary Services
 */
class ServiceAccessLayer(val baseUrl: URL)(implicit actorSystem: ActorSystem) {

  import ServicesClientJsonProtocol._
  import SmrtLinkServicesModels._
  import SprayJsonSupport._

  object ServiceEndpoints {
    val ROOT_JM = "/secondary-analysis/job-manager"
    val ROOT_JOBS = ROOT_JM + "/jobs"
    val ROOT_DS = "/secondary-analysis/datasets"
    val ROOT_PT = "/secondary-analysis/resolved-pipeline-templates"
  }

  object ServiceResourceTypes {
    val REPORTS = "reports"
    val DATASTORE = "datastore"
    val ENTRY_POINTS = "entry-points"
  }

  // FIXME this should probably use com.pacbio.secondary.analysis.jobtypes
  object JobTypes {
    val IMPORT_DS = "import-dataset"
    val IMPORT_DSTORE = "import-datastore"
    val MERGE_DS = "merge-datasets"
    val PB_PIPE = "pbsmrtpipe"
    val MOCK_PB_PIPE = "mock-pbsmrtpipe"
    val CONVERT_FASTA = "convert-fasta-reference"
  }

  // Context to run futures in
  implicit val executionContext = actorSystem.dispatcher

  private def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString
  private def toJobUrl(jobType: String, jobId: Int): String = {
    toUrl(s"${ServiceEndpoints.ROOT_JOBS}/${jobType}/${jobId}")
  }
  private def toJobResourceUrl(jobType: String, jobId: Int, resourceType: String): String = {
    toUrl(s"${ServiceEndpoints.ROOT_JOBS}/${jobType}/${jobId}/${resourceType}")
  }

  // Pipelines and serialization
  def respPipeline: HttpRequest => Future[HttpResponse] = sendReceive
  def serviceStatusPipeline: HttpRequest => Future[ServiceStatus] = sendReceive ~> unmarshal[ServiceStatus]
  def getDataSetByUuidPipeline: HttpRequest => Future[DataSetMetaDataSet] = sendReceive ~> unmarshal[DataSetMetaDataSet]
  def getJobPipeline: HttpRequest => Future[EngineJob] = sendReceive ~> unmarshal[EngineJob]
  def getDataStorePipeline: HttpRequest => Future[PacBioDataStore] = sendReceive ~> unmarshal[PacBioDataStore]
  def importDataSetPipeline: HttpRequest => Future[EngineJob] = sendReceive ~> unmarshal[EngineJob]

  val statusUrl = toUrl("/status")

  def getStatus: Future[ServiceStatus] = serviceStatusPipeline {
    Get(statusUrl)
  }

  // FIXME this should take either an Int or a UUID, but how?
  def getDataSetById(datasetId: Int): Future[DataSetMetaDataSet] = getDataSetByUuidPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DS + "/" + datasetId))
  }

  def getDataSetByUuid(datasetId: UUID): Future[DataSetMetaDataSet] = getDataSetByUuidPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DS + "/" + datasetId))
  }

  def getJobById(jobId: Int): Future[EngineJob] = getJobPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobId))
  }

  def getJobByTypeAndId(jobType: String, jobId: Int): Future[EngineJob] = getJobPipeline {
    Get(toJobUrl(jobType, jobId))
  }

  def getAnalysisJobById(jobId: Int): Future[EngineJob] = getJobPipeline {
    Get(toJobUrl(JobTypes.PB_PIPE, jobId))
  }

  def getAnalysisJobDataStore(jobId: Int): Future[PacBioDataStore] = getDataStorePipeline {
    Get(toJobResourceUrl(JobTypes.PB_PIPE, jobId, ServiceResourceTypes.DATASTORE))
  }

  def getImportDatasetJobDataStore(jobId: Int): Future[PacBioDataStore] = getDataStorePipeline {
    Get(toJobResourceUrl(JobTypes.IMPORT_DS, jobId, ServiceResourceTypes.DATASTORE))
  }

  def getMergeDatasetJobDataStore(jobId: Int): Future[PacBioDataStore] = getDataStorePipeline {
    Get(toJobResourceUrl(JobTypes.MERGE_DS, jobId, ServiceResourceTypes.DATASTORE))
  }

  def importDataSet(path: String, dsMetaType: String): Future[EngineJob] = importDataSetPipeline {
    Post(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + JobTypes.IMPORT_DS),
         CreateDataSet(path, dsMetaType))
  }

}
