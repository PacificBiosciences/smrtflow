package com.pacbio.secondary.smrtlink.actors

import java.net.URL
import java.nio.file.Path

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.models.{ExternalServerStatus, PacBioDataBundle, PacBioDataBundleIO}

import scala.concurrent.Future
import scala.util.control.NonFatal


object PacBioDataBundlePollExternalActor {
  case object CheckForUpdates
  case object CheckStatus // Sanity call to get list of bundles
}

/**
  * Actor used to poll an external server and download newer bundles to the System root bundle dir.
  *
  * On startup, the system will trigger an external call to check for newer bundles.
  *
  * @param rootBundleDir System root bundle dir
  * @param url           Root URL of the external bundle server
  * @param pollTime      interval time between polling the external server
  * @param daoActor      Bundle DAO. All access to the Data Bundle DAO should be done via the Actor interface to ensure thread safe behavior.
  * @param bundleType    Bundle type to check for upgrades
  */
class PacBioDataBundlePollExternalActor(rootBundleDir: Path, url: Option[URL], pollTime: FiniteDuration, daoActor: ActorRef, bundleType: String = "chemistry") extends Actor with LazyLogging{
  import PacBioDataBundlePollExternalActor._
  import PacBioBundleDaoActor.{GetAllBundlesByType, AddBundleIO}

  val initialDelay = 5.seconds

  implicit val timeOut:Timeout = Timeout(FiniteDuration(120, SECONDS))

  // schedule an Initial Check on Startup
  context.system.scheduler.scheduleOnce(initialDelay, self, CheckForUpdates)

  context.system.scheduler.schedule(initialDelay,  pollTime, self, CheckForUpdates)

  val client: Option[SmrtLinkServiceAccessLayer] =
    url.map(u => new SmrtLinkServiceAccessLayer(u, None)(context.system))

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"Creating $self with system root bundle dir:$rootBundleDir External URL $url")
  }

  override def preRestart(reason:Throwable, message:Option[Any]){
    super.preRestart(reason, message)
    logger.error(s"(preRestart) Unhandled exception ${reason.getMessage} Message $message")
  }

  /**
    * Sanity check for the bundle service client to get a list of bundles from the external server.
    *
    * @param c Bundle Client
    * @return
    */
  def getStatus(c: SmrtLinkServiceAccessLayer): Future[ExternalServerStatus] = {
    c.getPacBioDataBundles().map { bs =>
      val summary = bs.map(b => s"${b.typeId}-${b.version}").reduce(_ + "," + _)
      val msg = s"External Bundle Service is OK. Successfully found ${bs.length} bundles from ${c.baseUrl}. All remote Bundles $summary"
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
  def downloadBundle(c: SmrtLinkServiceAccessLayer, b: PacBioDataBundle, rootOutputDir: Path): PacBioDataBundleIO = {
    val ux = s"${c.baseUrl}/smrt-link/bundles/${b.typeId}/${b.version}/download"
    logger.info(s"Attempting to download Bundle ${b.typeId} ${b.version} from $ux")
    val bundleUrl = new URL(ux)
    val bio = PacBioDataBundleIOUtils.downloadAndProcessDataBundle(bundleUrl, rootOutputDir)
    logger.info(s"Downloaded bundle $bio")
    bio
  }

  private def andLog[T](msg: String, bs: Seq[T]): Seq[T] = {
    logger.info(s"$msg with $bs")
    bs
  }

  /**
    * Get the next newest upgrade bundle
    *
    * @param c Bundle Client
    * @return
    */
  def getNewestBundle(c: SmrtLinkServiceAccessLayer): Future[Option[PacBioDataBundle]] = {
    for {
      myBundles <- (daoActor ? GetAllBundlesByType(bundleType)).mapTo[Seq[PacBioDataBundle]]
      externalBundles <- c.getPacBioDataBundleByTypeId(bundleType)
      sortedExternalBundles <- Future.successful(andLog[PacBioDataBundle]("All external Server Bundles", PacBioBundleUtils.sortByVersion(externalBundles)))
      newBundles <- Future.successful(andLog[PacBioDataBundle]("New bundles", sortedExternalBundles.filter(b => PacBioBundleUtils.getBundle(myBundles, b.typeId, b.version).isEmpty)))
    } yield newBundles.headOption
  }

  /**
    * Get only newest bundle from external server and download to the system bundle root dir.
    *
    * If there are multiple upgrades available, the bundles will be downloaded sequentially.
    *
    *
    * @param c Bundle Client
    * @return
    */
  def checkAndDownload(c: SmrtLinkServiceAccessLayer): Future[Option[PacBioDataBundleIO]] = {
    logger.info(s"Checking for new bundles to ${c.baseUrl}")
    getNewestBundle(c).map {
      case Some(b) =>
        logger.info(s"Found new bundle $b")
        Some(downloadBundle(c, b, rootBundleDir))
      case _ =>
        logger.info(s"No new bundles found for ${c.baseUrl}")
        None
    }
  }

  /**
    * Check for new bundles, download only the newest (if avail) and update the PacBio Data bundle
    * registry with the new bundle.
    *
    * Note, this does NOT set the bundle as active. This must be set explicitly by the user.
    *
    * @param c Bundle Client
    * @return
    */
  def checkDownloadUpdate(c: SmrtLinkServiceAccessLayer): Future[Option[PacBioDataBundleIO]] = {
    checkAndDownload(c).flatMap {
      case Some(bio) =>
        val f = for {
          b <- (daoActor ? AddBundleIO(bio)).mapTo[PacBioDataBundleIO]
        } yield Some(b)
        f
      case _ =>
        logger.debug(s"No '$bundleType' Bundle upgrades found for ${c.baseUrl}")
        Future.successful(None)
    }
  }

  /**
    * Check, Download, Update registry and Handle/log errors
    *
    * @param c Bundle Client
    * @return
    */
  def checkDownloadUpgradeAndHandle(c: SmrtLinkServiceAccessLayer): Future[Option[PacBioDataBundleIO]] = {
    val f = checkDownloadUpdate(c)

    f.onSuccess {
      case Some(b:PacBioDataBundleIO) => logger.info(s"Successfully added bundle to registry $b")
      case None => logger.info("No bundles found to upgrade")
    }

    f.onFailure {
      case ex: Exception =>
        logger.error(s"Failed to add bundle to registry ${ex.getMessage}")
    }
    f
  }


  override def receive = {
    case CheckForUpdates =>
      // This is just a method to trigger an check of the potential upgrade. It doesn't block
      val msg = s"Checking $url for updates for bundle type $bundleType"
      sender ! MessageResponse(msg)

      client match {
        case Some(c) => checkDownloadUpgradeAndHandle(c)
        case _ =>
          logger.info("No external bundle URL provided. Skipping Check for Upgrades")
      }

    case CheckStatus =>
      logger.info(s"Checking status of $url")
      val fx = client.map(c => getStatus(c).recover { case NonFatal(ex) => ExternalServerStatus(s"Unable to connect to ${c.baseUrl} ${ex.getMessage}", "DOWN")})
          .getOrElse(Future.successful(ExternalServerStatus("No URL configured. Skipping status check", "UP")))
      fx pipeTo sender()

  }
}

trait PacBioDataBundlePollExternalActorProvider {
  this: ActorRefFactoryProvider with SmrtLinkConfigProvider with PacBioBundleDaoActorProvider =>

  val externalBundleUpgraderActor: Singleton[ActorRef] =
    Singleton(() =>  actorRefFactory().actorOf(Props(classOf[PacBioDataBundlePollExternalActor], pacBioBundleRoot(), externalBundleUrl(), externalBundlePollDuration(), pacBioBundleDaoActor(), "chemistry")))

}

