package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.models.{PacBioDataBundle, SmrtLinkJsonProtocols}
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


// This is largely duplicated between ServiceAccessLayer
// The Service AccessLayer needs to extend a base trait
// to enable better extensibility. This should be able
// to be mixed-in to SAL and define toPacBioDataBundleUrl
// and everything should work as expected.
trait PacBioDataBundleClientTrait extends ClientBase{
  import SprayJsonSupport._
  import SmrtLinkJsonProtocols._

  /**
    * This will resolve the URL to bundle root service.
    *
    * @param bundleType
    * @return
    */
  def toPacBioDataBundleUrl(bundleType: Option[String] = None): String

  def sendReceiveAuthenticated:HttpRequest ⇒ Future[HttpResponse]

  def getPacBioDataBundlesPipeline: HttpRequest => Future[Seq[PacBioDataBundle]] = sendReceiveAuthenticated ~> unmarshal[Seq[PacBioDataBundle]]
  def getPacBioDataBundlePipeline: HttpRequest => Future[PacBioDataBundle] = sendReceiveAuthenticated ~> unmarshal[PacBioDataBundle]

  // PacBio Data Bundle
  def getPacBioDataBundles() = getPacBioDataBundlesPipeline { Get(toPacBioDataBundleUrl()) }

  def getPacBioDataBundleByTypeId(typeId: String) =
    getPacBioDataBundlesPipeline { Get(toPacBioDataBundleUrl(Some(typeId))) }

  def getPacBioDataBundleByTypeAndVersionId(typeId: String, versionId: String) =
    getPacBioDataBundlePipeline { Get(toPacBioDataBundleUrl(Some(s"$typeId/$versionId")))}
}


/**
  * The Bundle Client to access PacBio Data Bundles.
  *
  * @param baseUrl     Root Base URL of the bundle services (e.g, smrt-link/bundles or /bundles)
  * @param actorSystem Actor System
  */
class PacBioDataBundleClient(override val baseUrl: URL)(implicit val actorSystem: ActorSystem) extends PacBioDataBundleClientTrait{

  override def sendReceiveAuthenticated = sendReceive

  def toPacBioDataBundleUrl(bundleType: Option[String] = None): String = {
    val segment = bundleType.map(b => s"/$b").getOrElse("")
    toUrl(segment)
  }



}
