package com.pacbio.secondary.smrtlink.services

import akka.util.Timeout
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.utils.{
  StatusGenerator,
  StatusGeneratorProvider
}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.duration._

class StatusService(statusGenerator: StatusGenerator) extends PacBioService {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(toServiceId("status"),
                                         "Status Service",
                                         "0.2.0",
                                         "Subsystem Status Service")

  val statusServiceName = "status"

  val routes =
    path(statusServiceName) {
      get {
        complete {
          statusGenerator.getStatus
        }
      }
    }
}

/**
  * Provides a singleton StatusService, and also binds it to the set of total services. Concrete providers must mixin a
  * {{{StatusServiceActorRefProvider}}}.
  */
trait StatusServiceProvider { this: StatusGeneratorProvider =>

  val statusService: Singleton[StatusService] =
    Singleton(() => new StatusService(statusGenerator()))
      .bindToSet(AllServices)
}

trait StatusServiceProviderx {
  this: StatusGeneratorProvider with ServiceComposer =>

  final val statusService: Singleton[StatusService] =
    Singleton(() => new StatusService(statusGenerator()))

  addService(statusService)
}
