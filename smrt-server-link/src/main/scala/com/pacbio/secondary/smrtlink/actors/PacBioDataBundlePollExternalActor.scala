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

class PacBioDataBundlePollExternalActor(rootBundleDir: Path, url: Option[URL], pollTime: FiniteDuration, daoActor: ActorRef, bundleType: String = "chemistry") extends Actor with LazyLogging{
  import PacBioDataBundlePollExternalActor._
  import PacBioBundleDaoActor.{GetAllBundlesByType, AddBundleIO}

  val initialDelay = 10.seconds

  implicit val timeOut:Timeout = Timeout(FiniteDuration(60, SECONDS))

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

  // This should be encapsulated in the Bundle Client
  def getStatus(c: SmrtLinkServiceAccessLayer): Future[ExternalServerStatus] = {
    c.getPacBioDataBundles().map { bs =>
      val msg = s"External Bundle Service is OK. Successfully found ${bs.length} bundles from ${c.baseUrl}"
      logger.debug(msg)
      ExternalServerStatus(msg, "UP")
    }
  }

  def downloadBundle(c: SmrtLinkServiceAccessLayer ,b: PacBioDataBundle, rootOutputDir: Path): PacBioDataBundleIO = {
    // this should be abstracted into the client layer
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

  def getNewestBundle(c: SmrtLinkServiceAccessLayer): Future[Option[PacBioDataBundle]] = {
    for {
      myBundles <- (daoActor ? GetAllBundlesByType(bundleType)).mapTo[Seq[PacBioDataBundle]]
      externalBundles <- c.getPacBioDataBundleByTypeId(bundleType)
      _ <- Future.successful(andLog[PacBioDataBundle]("External Server Bundles", externalBundles))
      newBundles <- Future.successful(andLog[PacBioDataBundle]("New bundles", externalBundles.filter(b => PacBioBundleUtils.getBundle(myBundles, b.typeId, b.version).isEmpty)))
    } yield newBundles.headOption
  }

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

  def checkDownloadUpdate(c: SmrtLinkServiceAccessLayer): Unit = {
    checkAndDownload(c).map {
      case Some(bio) =>
        val f = (daoActor ? AddBundleIO(bio)).mapTo[PacBioDataBundleIO]
        f.onSuccess { case b: PacBioDataBundleIO => logger.info(s"Successfully added bundle to registry $b") }
        f.onFailure {
          case ex: Exception =>
            logger.error(s"Failed to add bundle to registry $bio ${ex.getMessage}")
        }
      case _ => logger.debug(s"No '$bundleType' Bundle upgrades found for ${c.baseUrl}")
    }
  }


  override def receive = {
    case CheckForUpdates =>
      // This is just a method to trigger an check of the potential upgrade. It doesn't block
      val msg = s"Checking $url for updates for bundle type $bundleType"
      sender ! MessageResponse(msg)
      client match {
        case Some(c) =>
          logger.info(msg)
          checkDownloadUpdate(c)
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

