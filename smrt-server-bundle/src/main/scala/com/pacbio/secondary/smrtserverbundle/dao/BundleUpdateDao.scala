package com.pacbio.secondary.smrtserverbundle.dao

import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils
import com.pacbio.secondary.smrtlink.models.PacBioDataBundleIO

import scala.concurrent.{ExecutionContext, Future}

/**
  *
  * Terms:
  * - system version
  * - bundle type
  * - bundle version
  *
  * Get a Bundle b
  *
  * Note, This is not designed to be thread-safe. The use of futures
  * is to make the interface play well with the WebService.
  */
class BundleUpdateDao(
    private val bundles: Map[String, Seq[PacBioDataBundleIO]])(
    implicit ec: ExecutionContext)
    extends DaoFutureUtils {

  def allBundles(): Future[Seq[PacBioDataBundleIO]] =
    Future.successful(bundles.values.flatten.toSeq)

  def getBundlesBySystem(
      systemVersion: String): Future[Seq[PacBioDataBundleIO]] =
    failIfNone(s"Unable to find bundles for system $systemVersion")(
      bundles.get(systemVersion))

  def getBundlesBySystemAndBundleType(
      systemVersion: String,
      bundleType: String): Future[Seq[PacBioDataBundleIO]] =
    getBundlesBySystem(systemVersion).map(bs =>
      bs.filter(_.bundle.typeId == bundleType))

  def getBundleByVersion(systemVersion: String,
                         bundleType: String,
                         bundleVersion: String): Future[PacBioDataBundleIO] = {
    val msg =
      s"Unable to find bundle type: '$bundleType' with bundle version '$bundleVersion' for system version '$systemVersion"

    failIfNone(msg)(
      bundles
        .get(systemVersion)
        .flatMap { bs =>
          bs.filter(_.bundle.typeId == bundleType)
            .find(_.bundle.version == bundleVersion)
        })
  }

}
