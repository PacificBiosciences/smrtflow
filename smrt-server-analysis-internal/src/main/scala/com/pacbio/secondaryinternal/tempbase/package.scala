package com.pacbio.secondaryinternal.tempbase

import java.net.BindException

import akka.actor.{Props, ActorSystem, ActorRefFactory}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout

import com.pacbio.common.actors._
import com.pacbio.common.auth.{AuthenticatorProvider, BaseRolesInit, FakeAuthenticatorProvider}
import com.pacbio.common.cleanup.CleanupSchedulerProvider
import com.pacbio.common.dependency.{DefaultConfigProvider, TypesafeSingletonReader, Singleton}
import com.pacbio.common.logging.LoggerFactory
import com.pacbio.common.models.{LogResourceRecord, PacBioJsonProtocol, PacBioComponentManifest}
import com.pacbio.common.services._
import com.pacbio.common.time.SystemClockProvider

import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}

import spray.can.Http
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing.{RouteConcatenation, Route}
import spray.servlet.WebBoot

// TODO(mskinner): move this stuff into BaseSmrtServer

trait CoreProviders extends
    ServiceComposer with
    DefaultConfigProvider with
    StatusServiceActorRefProvider with
    StatusServiceProvider with
    HealthServiceProvider with
    ManifestServiceProvider with
    HealthServiceActorRefProvider with
    InMemoryHealthDaoProvider with
    FakeAuthenticatorProvider with
    ActorSystemProvider with
    SystemClockProvider {

  val serverHost: Singleton[String] = TypesafeSingletonReader.fromConfig().getString("host").orElse("0.0.0.0")
  val serverPort: Singleton[Int] = TypesafeSingletonReader.fromConfig().getInt("port").orElse(8080)

  override val actorSystemName = Some("smrt-server-analysis-internal")

  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrt_server_analysis_internal")

  //override val logDaoDatabaseConfigProvider: DatabaseConfigProvider = new TypesafeDatabaseConfigProvider {
  //  override val databaseConfigPath = Singleton("log")
  //}

  //override val lazyLogging: Singleton[Boolean] = Singleton(true)
}

trait ServiceComposer extends RouteConcatenation with RouteProvider {
  val services = ArrayBuffer.empty[Singleton[PacBioService]]

  def addService(service: Singleton[PacBioService]) = {
    services += service
  }

  def routes(): Route = {
    services.map(_().prefixedRoutes).reduce(_ ~ _)
  }

  def manifests(): Seq[PacBioComponentManifest] = {
    services.map(_().manifest)
  }
}

class ManifestService(services: ServiceComposer) extends PacBioService with DefaultJsonProtocol {

  import PacBioJsonProtocol._

  val manifest = PacBioComponentManifest(
    toServiceId("service_manifests"),
    "Status Service",
    "0.2.0", "Subsystem Manifest Service")

  val routes =
    path("services" / "manifests") {
      get {
        complete {
          services.manifests()
        }
      }
    }
}

trait ManifestServiceProvider {
  this: ServiceComposer =>

  final val manifestService: Singleton[ManifestService] =
    Singleton(() => new ManifestService(this))

  addService(manifestService)
}

trait HealthServiceProvider {
  this: HealthServiceActorRefProvider
      with AuthenticatorProvider
      with ServiceComposer =>

  final val healthService: Singleton[HealthService] =
    Singleton(() => new HealthService(healthServiceActorRef(), authenticator()))

  addService(healthService)
}

trait StatusServiceProvider {
  this: StatusServiceActorRefProvider
      with ServiceComposer =>

  final val statusService: Singleton[StatusService] =
    Singleton(() => new StatusService(statusServiceActorRef()))

  addService(statusService)
}
