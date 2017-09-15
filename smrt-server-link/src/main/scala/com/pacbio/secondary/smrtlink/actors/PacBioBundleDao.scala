package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Files, Path}
import scala.collection.mutable

import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  ResourceNotFoundError,
  UnprocessableEntityError
}
import com.pacbio.secondary.smrtlink.PacBioDataBundleConstants
import com.pacbio.secondary.smrtlink.models.{
  PacBioDataBundle,
  PacBioDataBundleIO,
  PacBioDataBundleUpgrade
}

import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

trait PacBioBundleUtils {
  def getBundlesByType(bundles: Seq[PacBioDataBundle],
                       bundleType: String): Seq[PacBioDataBundle] =
    bundles.filter(_.typeId == bundleType)

  /**
    * Return the newest version of bundle as defined by SemVer
    * @param bundleType
    * @return
    */
  def getNewestBundleVersionByType(
      bundles: Seq[PacBioDataBundle],
      bundleType: String): Option[PacBioDataBundle] = {
    implicit val orderBy = PacBioDataBundle.orderByBundleVersion
    getBundlesByType(bundles, bundleType).sorted.reverse.headOption
  }

  def getBundle(bundles: Seq[PacBioDataBundle],
                bundleType: String,
                version: String): Option[PacBioDataBundle] =
    getBundlesByType(bundles, bundleType).find(_.version == version)

  def sortByVersion(bundles: Seq[PacBioDataBundle]): Seq[PacBioDataBundle] = {
    implicit val orderBy = PacBioDataBundle.orderByBundleVersion
    bundles.sorted
  }
}

object PacBioBundleUtils extends PacBioBundleUtils

class PacBioBundleDao(
    bundles: Seq[PacBioDataBundleIO] = Seq.empty[PacBioDataBundleIO])
    extends PacBioDataBundleConstants {

  private var loadedBundles = mutable.ArrayBuffer.empty[PacBioDataBundleIO]

  bundles.foreach(b => loadedBundles += b)

  def getBundles = loadedBundles.map(_.bundle).toList

  def getBundlesByType(bundleType: String): Seq[PacBioDataBundle] =
    PacBioBundleUtils.getBundlesByType(loadedBundles.map(_.bundle), bundleType)

  def getNewestBundleVersionByType(
      bundleType: String): Option[PacBioDataBundle] =
    PacBioBundleUtils.getNewestBundleVersionByType(getBundles, bundleType)

  def getActiveBundleVersionByType(
      bundleType: String): Option[PacBioDataBundle] =
    getBundles.find(_.isActive)

  def getActiveBundleByType(bundleType: String): Option[PacBioDataBundle] =
    getBundles.filter(_.typeId == bundleType).find(_.isActive == true)

  def getActiveBundleIOByType(bundleType: String): Option[PacBioDataBundleIO] =
    loadedBundles
      .filter(_.bundle.typeId == bundleType)
      .find(_.bundle.isActive == true)

  def getBundle(bundleType: String,
                version: String): Option[PacBioDataBundle] =
    PacBioBundleUtils
      .getBundlesByType(getBundles, bundleType)
      .find(_.version == version)

  def getBundleIO(bundleType: String,
                  version: String): Option[PacBioDataBundleIO] =
    loadedBundles.find(b =>
      (b.bundle.version == version) && (b.bundle.typeId == bundleType))

  def getBundlesIO(bundleType: String): Seq[PacBioDataBundleIO] =
    loadedBundles.filter(_.bundle.typeId == bundleType)

  def addBundle(bundle: PacBioDataBundleIO): PacBioDataBundleIO = {
    loadedBundles += bundle
    bundle
  }

  /**
    * Get the newest upgrade possible for a given bundle type.
    *
    * @param bundleType
    * @return
    */
  def getBundleUpgrade(bundleType: String): PacBioDataBundleUpgrade = {
    implicit val orderBy = PacBioDataBundle.orderByBundleVersion

    val activeBundle = getActiveBundleByType(bundleType)

    val bundles = loadedBundles
      .map(_.bundle)
      .filter(_.typeId == bundleType)
      .filter(_.isActive == false)

    val opt = activeBundle match {
      case Some(acBundle) =>
        bundles
          .filter(b => b.version > acBundle.version)
          .sorted
          .reverse
          .headOption
      case _ => None
    }

    PacBioDataBundleUpgrade(opt)
  }

  private def updateActiveSymLink(rootBundleDir: Path,
                                  bundleTypeId: String,
                                  bundlePath: Path): Path = {
    val px = rootBundleDir.resolve(s"$bundleTypeId-$ACTIVE_SUFFIX")
    if (Files.exists(px)) {
      px.toFile.delete()
    }
    Files.createSymbolicLink(px, bundlePath)
    px
  }

  private def setBundleAsActive(
      bundleType: String,
      bundleVersion: String): Try[PacBioDataBundleIO] = {
    val errorMessage =
      s"Unable to find $bundleType with version $bundleVersion"
    val newBundles = loadedBundles.map { b =>
      val bx = if (b.bundle.typeId == bundleType) {
        if (b.bundle.version == bundleVersion) b.bundle.copy(isActive = true)
        else b.bundle.copy(isActive = false)
      } else {
        b.bundle
      }
      b.copy(bundle = bx)
    }
    loadedBundles = newBundles

    getActiveBundleIOByType(bundleType)
      .map(b => Success(b))
      .getOrElse(Failure(new ResourceNotFoundError(errorMessage)))
  }

  /**
    * Mark the bundle as "Active" and create a symlink to the new active bundle with {bundle-type}-latest
    *
    * 1. Check if bundle is already active. Return bundle if so
    * 2.
    *
    * FIXME. This needs to be converted to {bundle-type}-active
    *
    * "latest" makes no sense.
    *
    * @param bundleType
    * @param bundleVersion
    * @return
    */
  def activateBundle(rootBundleDir: Path,
                     bundleType: String,
                     bundleVersion: String): Try[PacBioDataBundleIO] = {
    val errorMessage =
      s"Unable to find $bundleType with version $bundleVersion"

    getBundleIO(bundleType, bundleVersion)
      .map { b =>
        val tx = for {
          px <- Try {
            updateActiveSymLink(rootBundleDir, b.bundle.typeId, b.path)
          }
          b <- setBundleAsActive(bundleType, bundleVersion)
        } yield b

        tx.recoverWith {
          case NonFatal(ex) =>
            Failure(
              new UnprocessableEntityError(
                errorMessage + s" ${ex.getMessage}"))
        }
      }
      .getOrElse(Failure(new ResourceNotFoundError(
        s"Unable to find $bundleType with version $bundleVersion")))
  }
}
