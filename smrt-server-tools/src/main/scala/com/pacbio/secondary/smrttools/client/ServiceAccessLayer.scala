// derived from PrimaryClient.scala in PAWS
package com.pacbio.secondary.smrttools.client

import com.pacbio.secondary.analysis.constants.{GlobalConstants, FileTypes}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.common.models._

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future

import java.net.URL
import java.util.UUID


object ServicesClientJsonProtocol extends SmrtLinkJsonProtocols

/**
 * Client to Primary Services
 */
class ServiceAccessLayer(val baseUrl: URL)(implicit actorSystem: ActorSystem) {

  import ServicesClientJsonProtocol._
  import SprayJsonSupport._

  object ServiceEndpoints {
    val ROOT_JM = "/secondary-analysis/job-manager"
    val ROOT_JOBs = ROOT_JM + "/jobs"
    val ROOT_DS = "/secondary-analysis/datasets"
    val ROOT_PT = "/secondary-analysis/resolved-pipeline-templates"
  }

  // Context to run futures in
  implicit val executionContext = actorSystem.dispatcher

  private def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString

  // Pipelines and serialization
  def respPipeline: HttpRequest => Future[HttpResponse] = sendReceive
  def serviceStatusPipeline: HttpRequest => Future[ServiceStatus] = sendReceive ~> unmarshal[ServiceStatus]
  def getDataSetByUuidPipeline: HttpRequest => Future[DataSetMetaDataSet] = sendReceive ~> unmarshal[DataSetMetaDataSet]

  val statusUrl = toUrl("/status")

  def getStatus: Future[ServiceStatus] = serviceStatusPipeline {
    Get(statusUrl)
  }

  // FIXME this should take either an Int or a UUID, but how?
  def getDataSetById(datasetId: Int): Future[DataSetMetaDataSet] = getDataSetByUuidPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DS + "/" + datasetId))
  }

}
