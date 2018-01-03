package com.pacbio.secondary.smrtlink.actors

import java.net.URL
import java.nio.file.Path

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import com.pacbio.secondary.smrtlink.dependency.Singleton
import CommonMessages.MessageResponse
import com.pacbio.common.semver.SemVersion
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.pacbio.secondary.smrtlink.client.PacBioDataBundleUpdateServerClient
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.models.{
  ExternalServerStatus,
  PacBioDataBundle,
  PacBioDataBundleIO
}

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object PacBioDataBundlePollExternalActor {
  case object CheckForUpdates
  case object CheckStatus // Sanity call to get list of bundles
}

/**
  * Actor used to poll an external server and download newer bundles to the System root bundle dir.
  *
  * On startup, the system will trigger an external call to check for newer bundles.
  *
  * If either the server URL, or the pacBioSystemVersion is not provided, the updating will be
  * disabled.
  *
  * @param rootBundleDir         System root bundle dir
  * @param url                   Root URL of the external bundle server (Example http://my-host:8080)
  * @param pollTime              interval time between polling the external server
  * @param daoActor              Bundle DAO. All access to the Data Bundle DAO should be done via the Actor interface to ensure thread safe behavior.
  * @param bundleType            Bundle type to check for upgrades
  * @param smrtLinkSystemVersion SMRT Link System Version (e.g., 5.0.0.12345)
  */
class PacBioDataBundlePollExternalActor(rootBundleDir: Path,
                                        url: Option[URL],
                                        pollTime: FiniteDuration,
                                        daoActor: ActorRef,
                                        bundleType: String,
                                        smrtLinkSystemVersion: Option[String])
    extends Actor
    with LazyLogging {
  import PacBioDataBundlePollExternalActor._
  import PacBioBundleDaoActor.{GetAllBundlesByType, AddBundleIO}

  val initialDelay = 5.seconds

  lazy val pacBioSystemVersion: Option[String] =
    smrtLinkSystemVersion.flatMap(smrtLinkSystemVersionToPacBioReleaseVersion)

  implicit val timeOut: Timeout = Timeout(FiniteDuration(120, SECONDS))

  context.system.scheduler.schedule(initialDelay,
                                    pollTime,
                                    self,
                                    CheckForUpdates)

  def smrtLinkSystemVersionToPacBioReleaseVersion(sx: String): Option[String] = {
    Try(SemVersion.parseWithSlop(sx)).toOption
      .map(vx => s"${vx.major}.${vx.minor}.${vx.patch}")
  }

  /**
    * There's some lackluster use of pacBioSystemVersion.get on pacBioSystem that assumes the client is None and will
    * never be called. This should be fixed.
    */
  def getClient(): Option[PacBioDataBundleUpdateServerClient] = {
    (url, pacBioSystemVersion) match {
      case (Some(ux), Some(vx)) =>
        logger.info(
          s"Updating will look for PacBio System Release version $vx updates from $ux")
        Some(new PacBioDataBundleUpdateServerClient(
          new URL(ux.getProtocol, ux.getHost, ux.getPort, ""))(context.system))
      case _ =>
        logger.warn(
          s"System is NOT configured for accessing PacBio Updates systemVersion:$pacBioSystemVersion URL:$url")
        None
    }
  }
  val client: Option[PacBioDataBundleUpdateServerClient] = getClient()

  override def preStart(): Unit = {
    super.preStart()
    logger.info(
      s"Creating $self with system root bundle dir:$rootBundleDir External URL $url and SL $smrtLinkSystemVersion and PB System Release Version $pacBioSystemVersion")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    logger.error(
      s"(preRestart) Unhandled exception ${reason.getMessage} Message $message")
  }

  /**
    * Sanity check for the bundle service client to get a list of bundles from the external server.
    *
    * @param c Bundle Client
    * @return
    */
  def getStatus(
      c: PacBioDataBundleUpdateServerClient): Future[ExternalServerStatus] = {
    c.getPacBioDataBundles().map { bs =>
      val summary =
        bs.map(b => s"${b.typeId}-${b.version}").reduce(_ + "," + _)
      val msg =
        s"External Bundle Service is OK. Successfully found ${bs.length} bundles from ${c.baseUrl}. All remote Bundles $summary"
      logger.info(msg)
      ExternalServerStatus(msg, "UP")
    }
  }

  /**
    * Download a specific bundle by type-id, version identifier to the system level PacBio Data Bundle Directory
    *
    * This should be better encapsulated in the Bundle Client.
    *
    * @param c Bundle Client
    * @param b Bundle to download
    * @param rootOutputDir System Root Bundle Directory
    * @return
    */
  def downloadBundle(c: PacBioDataBundleUpdateServerClient,
                     b: PacBioDataBundle,
                     rootOutputDir: Path): PacBioDataBundleIO = {
    val downloadUrl = new URL(
      c.toV2PacBioBundleDownloadUrl(pacBioSystemVersion.get,
                                    b.typeId,
                                    b.version))
    logger.info(
      s"Attempting to download Bundle ${b.typeId} ${b.version} from $downloadUrl")
    val bio = PacBioDataBundleIOUtils.downloadAndProcessDataBundle(
      downloadUrl,
      rootOutputDir)
    logger.info(s"Downloaded bundle $bio")
    bio
  }

  private def andLog[T](msg: String, bs: Seq[T]): Seq[T] = {
    logger.info(s"$msg with $bs")
    bs
  }

  /**
    * Get bundles that we don't already have
    *
    * @param c Bundle Client
    * @return
    */
  def getNewBundles(
      c: PacBioDataBundleUpdateServerClient): Future[Seq[PacBioDataBundle]] = {
    for {
      myBundles <- (daoActor ? GetAllBundlesByType(bundleType))
        .mapTo[Seq[PacBioDataBundle]]
      externalBundles <- c.getV2PacBioDataBundleByTypeId(
        pacBioSystemVersion.get,
        bundleType)
      sortedExternalBundles <- Future.successful(
        andLog[PacBioDataBundle](
          "All external Server Bundles",
          PacBioBundleUtils.sortByVersion(externalBundles)))
      newBundles <- Future.successful(
        andLog[PacBioDataBundle]("New bundles",
                                 sortedExternalBundles.filter(b =>
                                   PacBioBundleUtils
                                     .getBundle(myBundles, b.typeId, b.version)
                                     .isEmpty)))
    } yield newBundles
  }

  /**
    * Get new bundles from external server and download to the system bundle root dir.
    *
    * If there are multiple upgrades available, the bundles will be downloaded sequentially.
    *
    *
    * @param c Bundle Client
    * @return
    */
  def checkAndDownload(c: PacBioDataBundleUpdateServerClient)
    : Future[Seq[PacBioDataBundleIO]] = {
    logger.info(s"Checking for new bundles to ${c.baseUrl}")
    getNewBundles(c).map { bundles =>
      if (bundles.isEmpty) {
        logger.info(s"No new bundles found for ${c.baseUrl}")
      }
      bundles.map { b =>
        logger.info(s"Found new bundle $b")
        downloadBundle(c, b, rootBundleDir)
      }
    }
  }

  /**
    * Check for new bundles, download the ones we don't already have, and update
    * the PacBio Data bundle registry with the new bundle.
    *
    * Note, this does NOT set the bundle as active. This must be set explicitly by the user.
    *
    * @param c Bundle Client
    * @return
    */
  def checkDownloadUpdate(c: PacBioDataBundleUpdateServerClient)
    : Future[Seq[Future[PacBioDataBundleIO]]] = {
    checkAndDownload(c).map { downloads =>
      if (downloads.isEmpty) {
        logger.debug(
          s"No '$bundleType' Bundle upgrades found for ${c.baseUrl}")
      }
      downloads.map(bio =>
        (daoActor ? AddBundleIO(bio)).mapTo[PacBioDataBundleIO])
    }
  }

  /**
    * Check, Download, Update registry and Handle/log errors
    *
    * @param c Bundle Client
    * @return
    */
  def checkDownloadUpgradeAndHandle(c: PacBioDataBundleUpdateServerClient)
    : Future[Seq[Future[PacBioDataBundleIO]]] = {
    checkDownloadUpdate(c).map { allFuts =>
      if (allFuts.isEmpty) {
        logger.info("No bundles found to upgrade")
      }

      allFuts.map { f =>
        f.onComplete {
          case Success(b) =>
            logger.info(s"Successfully added bundle to registry $b")
          case Failure(ex) =>
            logger.error(s"Failed to add bundle to registry ${ex.getMessage}")
        }
        f
      }
    }
  }

  override def receive = {
    case CheckForUpdates =>
      // This is just a method to trigger an check of the potential upgrade. It doesn't block
      val msg = url
        .map(u => s"Checking $u for updates for bundle type $bundleType")
        .getOrElse("No external data bundle URL configured. Skipping check.")
      sender ! MessageResponse(msg)

      client match {
        case Some(c) => checkDownloadUpgradeAndHandle(c)
        case _ =>
          logger.info(
            "No external bundle URL provided. Skipping Check for Upgrades")
      }

    case CheckStatus =>
      logger.info(s"Checking status of $url")
      val fx = client
        .map(c =>
          getStatus(c).recover {
            case NonFatal(ex) =>
              ExternalServerStatus(
                s"Unable to connect to ${c.baseUrl} ${ex.getMessage}",
                "DOWN")
        })
        .getOrElse(
          Future.successful(
            ExternalServerStatus("No URL configured. Skipping status check",
                                 "UP")))
      fx pipeTo sender()

  }
}

trait PacBioDataBundlePollExternalActorProvider {
  this: ActorRefFactoryProvider
    with SmrtLinkConfigProvider
    with PacBioBundleDaoActorProvider =>

  val externalBundleUpgraderActor: Singleton[ActorRef] =
    Singleton(
      () =>
        actorRefFactory().actorOf(Props(
          classOf[PacBioDataBundlePollExternalActor],
          pacBioBundleRoot(),
          externalBundleUrl(),
          externalBundlePollDuration(),
          pacBioBundleDaoActor(),
          chemistryBundleId,
          smrtLinkVersion()
        )))

}
