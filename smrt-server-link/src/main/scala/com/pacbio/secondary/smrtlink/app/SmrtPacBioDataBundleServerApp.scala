package com.pacbio.secondary.smrtlink.app

import java.net.BindException
import java.nio.file.Paths

import akka.actor.Props
import akka.io.IO
import akka.util.Timeout
import akka.pattern._
import com.pacbio.common.models.Constants
import com.pacbio.common.services.utils.StatusGenerator
import com.pacbio.common.services.{PacBioService, RoutedHttpService, StatusService}
import com.pacbio.common.time.SystemClock
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.actors.{PacBioBundleDao, PacBioBundleDaoActor, PacBioDataBundlePollExternalActor}
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.services.PacBioBundleService
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http
import spray.routing._

import concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Thin PacBio Data Bundle Only Server
  *
  */
trait PacBioDataBundleConfigCakeProvider extends BaseServiceConfigCakeProvider {
  override lazy val systemName = "bundle-server"

  lazy val pacBioBundleRoot = Paths.get(conf.getString("smrtflow.server.bundleDir")).toAbsolutePath()

}

trait PacBioDataBundleServicesCakeProvider {
  this: ActorSystemCakeProvider with PacBioDataBundleConfigCakeProvider =>

  lazy val statusGenerator = new StatusGenerator(new SystemClock(), systemName, systemUUID, Constants.SMRTFLOW_VERSION)

  lazy val loadedBundles = PacBioDataBundleIOUtils.loadBundlesFromRoot(pacBioBundleRoot)
  lazy val dao = new PacBioBundleDao(loadedBundles)
  lazy val daoActor = actorSystem.actorOf(Props(new PacBioBundleDaoActor(dao, pacBioBundleRoot)))
  // This is not the greatest model. If the URL is None, then none of the calls will be made
  lazy val externalPollActor = actorSystem.actorOf(Props(new PacBioDataBundlePollExternalActor(pacBioBundleRoot, None, 12.hours, daoActor)))

  lazy val services: Seq[PacBioService] = Seq(
    new PacBioBundleService(daoActor, pacBioBundleRoot, externalPollActor),
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
