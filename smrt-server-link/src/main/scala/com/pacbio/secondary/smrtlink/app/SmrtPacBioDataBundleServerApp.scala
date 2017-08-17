package com.pacbio.secondary.smrtlink.app

import java.net.{BindException, URL}
import java.nio.file.{Path, Paths}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.io.IO
import akka.util.Timeout
import akka.pattern._
import com.pacbio.common.models.Constants
import com.pacbio.secondary.smrtlink.services.utils.StatusGenerator
import com.pacbio.secondary.smrtlink.services.{PacBioService, RoutedHttpService}
import com.pacbio.secondary.smrtlink.time.SystemClock
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.actors.{EventManagerActor, PacBioBundleDao, PacBioBundleDaoActor, PacBioDataBundlePollExternalActor}
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.services.{PacBioBundleService, StatusService}
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http
import spray.routing._

import concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class ChemistryUpdateBundleService(daoActor: ActorRef, rootBundle: Path, externalPollActor: ActorRef, eventManagerActor: ActorRef)(implicit override val actorSystem: ActorSystem) extends PacBioBundleService(daoActor, rootBundle, externalPollActor, eventManagerActor)(actorSystem) {

  /**
    * Removed any PUT, POST routes yield only GET
    *
    * And remove the Remote bundle Status routes (this system should never be configured with an external
    * bundle service to update from.
    */
  override val routes = bundleRoutes
}

/**
  * Thin PacBio Data Bundle Only Server
  *
  */
trait PacBioDataBundleConfigCakeProvider extends BaseServiceConfigCakeProvider
    with LazyLogging {
  override lazy val systemName = "bundle-server"

  lazy val pacBioBundleRoot = Paths.get(conf.getString("smrtflow.server.bundleDir")).toAbsolutePath()

  lazy val dnsName = Try { conf.getString("smrtflow.server.dnsName") }.toOption

  /**
    * This will load the key and convert to URL.
    * Any errors will *only* be logged. This is probably not the best model.
    *
    * @return
    */
  private def loadUrl(key: String): Option[URL] = {
    Try { new URL(conf.getString(key))} match {
      case Success(url) =>
        logger.info(s"Converted $key to URL $url")
        Some(url)
      case Failure(ex) =>
        logger.error(s"Failed to load URL from key '$key' ${ex.getMessage}")
        None
    }
  }

  lazy val externalEveUrl = loadUrl("smrtflow.server.eventUrl")

  // This is the SMRT Link UI PORT that is host via https
  val smrtLinkUiPort: Int = 8243
}

trait PacBioDataBundleServicesCakeProvider {
  this: ActorSystemCakeProvider with PacBioDataBundleConfigCakeProvider =>

  lazy val statusGenerator = new StatusGenerator(new SystemClock(), systemName, systemUUID, Constants.SMRTFLOW_VERSION)

  lazy val loadedBundles = PacBioDataBundleIOUtils.loadBundlesFromRoot(pacBioBundleRoot)
  lazy val dao = new PacBioBundleDao(loadedBundles)
  lazy val daoActor = actorSystem.actorOf(Props(new PacBioBundleDaoActor(dao, pacBioBundleRoot)))
  // This is not the greatest model. If the URL is None, then none of the calls will be made
  lazy val externalPollActor = actorSystem.actorOf(Props(new PacBioDataBundlePollExternalActor(pacBioBundleRoot, None, 12.hours, daoActor)))
  lazy val eventManagerActor = actorSystem.actorOf(Props(new EventManagerActor(systemUUID, dnsName, externalEveUrl, apiSecret, smrtLinkUiPort)), "EventManagerActor")

  lazy val services: Seq[PacBioService] = Seq(
    new ChemistryUpdateBundleService(daoActor, pacBioBundleRoot, externalPollActor, eventManagerActor),
    new StatusService(statusGenerator))
}

trait RootPacBioDataBundleServerCakeProvider extends RouteConcatenation {
  this: ActorSystemCakeProvider with PacBioDataBundleServicesCakeProvider =>

  lazy val allRoutes:Route = services.map(_.prefixedRoutes).reduce(_ ~ _)

  lazy val rootService = actorSystem.actorOf(Props(new RoutedHttpService(allRoutes)))
}

trait PacBioDataBundleServerCakeProvider extends LazyLogging with timeUtils {
  this: RootPacBioDataBundleServerCakeProvider
      with PacBioDataBundleConfigCakeProvider
      with ActorSystemCakeProvider =>

  implicit val timeout = Timeout(10.seconds)

  //FIXME(mpkocher)(2017-4-11) Add validation on startup
  def startServices(): Future[String] = {
    (IO(Http)(actorSystem) ? Http.Bind(rootService, systemHost, port = systemPort)) flatMap  {
      case r: Http.CommandFailed => Future.failed(new BindException(s"Failed to bind to $systemHost:$systemPort"))
      case _ => Future {s"Successfully started up on $systemHost:$systemPort" }
    }
  }
}

object PacBioDataBundleServer extends PacBioDataBundleConfigCakeProvider
    with ActorSystemCakeProvider
    with PacBioDataBundleServicesCakeProvider
    with RootPacBioDataBundleServerCakeProvider
    with PacBioDataBundleServerCakeProvider {}


object SmrtPacBioDataBundleServerApp extends App{
  import PacBioDataBundleServer._
  LoggerOptions.parseAddDebug(args)
  startServices()
}
