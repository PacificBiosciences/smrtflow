package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.utils.{StatusGeneratorProvider, StatusGenerator}
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.duration._

class StatusService(statusGenerator: StatusGenerator) extends PacBioService {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("status"),
    "Status Service",
    "0.2.0", "Subsystem Status Service")

  val statusServiceName = "status"

  val routes =
    path(statusServiceName) {
      get {
        complete {
          ok {
            statusGenerator.getStatus
          }
        }
      }
    }
}

/**
 * Provides a singleton StatusService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{StatusServiceActorRefProvider}}}.
 */
trait StatusServiceProvider {
  this: StatusGeneratorProvider =>

  val statusService: Singleton[StatusService] =
    Singleton(() => new StatusService(statusGenerator())).bindToSet(AllServices)
}

trait StatusServiceProviderx {
  this: StatusGeneratorProvider
    with ServiceComposer =>

  final val statusService: Singleton[StatusService] =
    Singleton(() => new StatusService(statusGenerator()))

  addService(statusService)
}
