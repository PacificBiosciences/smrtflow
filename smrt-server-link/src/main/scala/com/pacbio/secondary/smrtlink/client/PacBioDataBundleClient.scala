package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.models.PacBioDataBundle
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.unmarshalling.Unmarshal

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// This is largely duplicated between ServiceAccessLayer
// The Service AccessLayer needs to extend a base trait
// to enable better extensibility. This should be able
// to be mixed-in to SAL and define toPacBioDataBundleUrl
// and everything should work as expected.
trait PacBioDataBundleClientTrait extends ClientBase {
  import SprayJsonSupport._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  /**
    * This will resolve the URL to bundle root service.
    *
    * @param bundleType
    * @return
    */
  def toPacBioDataBundleUrl(bundleType: Option[String] = None): String

  /**
    * Get Bundles of All Types
    */
  def getPacBioDataBundles(): Future[Seq[PacBioDataBundle]] =
    http
      .singleRequest(Get(toPacBioDataBundleUrl()))
      .flatMap(Unmarshal(_).to[Seq[PacBioDataBundle]])

  /**
    * Get Bundles of a specific Type
    */
  def getPacBioDataBundleByTypeId(
      typeId: String): Future[Seq[PacBioDataBundle]] =
    http
      .singleRequest(Get(toPacBioDataBundleUrl(Some(typeId))))
      .flatMap(Unmarshal(_).to[Seq[PacBioDataBundle]])

  /**
    * Get a Specific Bundle by Type and Version
    */
  def getPacBioDataBundleByTypeAndVersionId(
      typeId: String,
      versionId: String): Future[Seq[PacBioDataBundle]] =
    http
      .singleRequest(Get(toPacBioDataBundleUrl(Some(s"$typeId/$versionId"))))
      .flatMap(Unmarshal(_).to[Seq[PacBioDataBundle]])

}

/**
  * The Bundle Client to ONLY access PacBio Data Bundles on SMRT Link.
  *
  *
  * @param baseUrl     Root Base URL of the bundle services (e.g, smrt-link/bundles)
  * @param actorSystem Actor System
  */
class PacBioDataBundleClient(override val baseUrl: URL)(
    implicit val actorSystem: ActorSystem)
    extends PacBioDataBundleClientTrait {

  def toPacBioDataBundleUrl(bundleType: Option[String] = None): String = {
    val segment = bundleType.map(b => s"/$b").getOrElse("")
    toUrl(segment)
  }
  def toPacBioBundleDownloadUrl(bundleType: String, bundleVersion: String) = {
    val segment = s"/$bundleType/$bundleVersion/download"
    toUrl(segment)
  }
}

/**
  * For <= 5.1.0, the Update Server and SMRT Link service interface has diverged.
  *
  * This client can access the legacy "V1" routes, or the new "V2" routes.
  *
  * @param baseUrl     Root Base URL of the bundle services (e.g, smrt-link/bundles)
  * @param actorSystem Actor System
  */
class PacBioDataBundleUpdateServerClient(override val baseUrl: URL)(
    implicit override val actorSystem: ActorSystem)
    extends PacBioDataBundleClient(baseUrl)(actorSystem) {

  import SprayJsonSupport._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val V2_PREFIX = "/api/v2/updates"

  def toV2PacBioDataBundleUrl(pacBioSystemVersion: String,
                              bundleType: Option[String] = None): String = {
    val segment = bundleType
      .map(b => s"$V2_PREFIX/$pacBioSystemVersion/bundles/$b")
      .getOrElse(s"$V2_PREFIX/$pacBioSystemVersion/bundles")
    toUrl(segment)
  }

  def toV2PacBioBundleDownloadUrl(pacBioSystemVersion: String,
                                  bundleType: String,
                                  bundleVersion: String): String = {
    val segment =
      s"$V2_PREFIX/$pacBioSystemVersion/bundles/$bundleType/$bundleVersion/download"
    toUrl(segment)
  }

  /**
    * Get Bundles of a specific Type
    */
  def getV2PacBioDataBundleByTypeId(
      pacBioSystemVersion: String,
      bundleType: String): Future[Seq[PacBioDataBundle]] =
    http
      .singleRequest(
        Get(toV2PacBioDataBundleUrl(pacBioSystemVersion, Some(bundleType))))
      .flatMap(Unmarshal(_).to[Seq[PacBioDataBundle]])

}
