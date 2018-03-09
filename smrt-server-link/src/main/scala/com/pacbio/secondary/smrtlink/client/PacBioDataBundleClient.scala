package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.models.PacBioDataBundle
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * The Bundle Client to ONLY access PacBio Data Bundles on SMRT Link.
  * @param actorSystem Actor System
  */
class PacBioDataBundleClient(host: String, port: Int)(
    implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(host, port)(actorSystem) {

  import SprayJsonSupport._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val BUNDLES_PATH = Uri.Path("smrt-link") / "bundles"

  /**
    * This will resolve the URL to bundle root service.
    *
    * @param bundleType
    * @return
    */
  def toPacBioDataBundleUriPath(bundleType: Option[String] = None): Uri.Path =
    bundleType.map(b => BUNDLES_PATH / b).getOrElse(BUNDLES_PATH)

  def toPacBioDataBundleUrl(bundleType: Option[String] = None): Uri =
    toUri(toPacBioDataBundleUriPath(bundleType))

  def toPacBioBundleDownloadUrl(bundleType: String,
                                bundleVersion: String): Uri = {
    val path = BUNDLES_PATH / bundleType / bundleVersion / "download"
    toUri(path)
  }

  /**
    * Get Bundles of All Types
    */
  def getPacBioDataBundles(): Future[Seq[PacBioDataBundle]] =
    getObject[Seq[PacBioDataBundle]](Get(toPacBioDataBundleUrl()))

  /**
    * Get Bundles of a specific Type
    */
  def getPacBioDataBundleByTypeId(
      typeId: String): Future[Seq[PacBioDataBundle]] =
    getObject[Seq[PacBioDataBundle]](Get(toPacBioDataBundleUrl(Some(typeId))))

  /**
    * Get a Specific Bundle by Type and Version
    */
  def getPacBioDataBundleByTypeAndVersionId(
      typeId: String,
      versionId: String): Future[Seq[PacBioDataBundle]] =
    getObject[Seq[PacBioDataBundle]](
      Get(toUri(toPacBioDataBundleUriPath(Some(s"$typeId")) / versionId)))

}

/**
  * For <= 5.1.0, the Update Server and SMRT Link service interface has diverged.
  *
  * This client can access the legacy "V1" routes, or the new "V2" routes.
  *
  * @param baseUrl     Root Base URL of the bundle services (e.g, smrt-link/bundles)
  * @param actorSystem Actor System
  */
class PacBioDataBundleUpdateServerClient(host: String, port: Int)(
    implicit actorSystem: ActorSystem)
    extends PacBioDataBundleClient(host, port)(actorSystem) {

  import SprayJsonSupport._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val V2_PREFIX_PATH: Uri.Path = Uri.Path("api") / "v2" / "updates"

  def toV2PacBioDataBundleUrl(pacBioSystemVersion: String,
                              bundleType: Option[String] = None): Uri = {

    val base = V2_PREFIX_PATH / pacBioSystemVersion / "bundles"

    toUri(bundleType.map(b => base / b).getOrElse(base))
  }

  def toV2PacBioBundleDownloadUrl(pacBioSystemVersion: String,
                                  bundleType: String,
                                  bundleVersion: String): Uri = {
    val path = V2_PREFIX_PATH / pacBioSystemVersion / "bundles" / bundleType / bundleVersion / "download"
    toUri(path)
  }

  /**
    * Get Bundles of a specific Type
    */
  def getV2PacBioDataBundleByTypeId(
      pacBioSystemVersion: String,
      bundleType: String): Future[Seq[PacBioDataBundle]] =
    getObject[Seq[PacBioDataBundle]](
      Get(toV2PacBioDataBundleUrl(pacBioSystemVersion, Some(bundleType))))

}
