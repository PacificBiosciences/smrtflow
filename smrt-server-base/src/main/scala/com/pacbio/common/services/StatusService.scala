package com.pacbio.common.services

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.actors.{StatusServiceActorRefProvider, StatusServiceActor}
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class StatusService(statusActor: ActorRef) extends PacBioService {

  import StatusServiceActor._
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
          for {
            id <- (statusActor ? GetBaseServiceId).mapTo[String].map(toServiceId)
            up <- (statusActor ? GetUptime).mapTo[Long]
            uuid <- (statusActor ? GetUUID).mapTo[UUID]
            ver <- (statusActor ? GetBuildVersion).mapTo[String]
            user <- (statusActor ? GetUser).mapTo[String]
          } yield ServiceStatus(id, s"Services have been up for ${uptimeString(up)}.", up, uuid, ver, user)
        }
      }
    }

  def uptimeString(uptimeMillis: Long): String = {
    val period = new Period(uptimeMillis)
    val formatter = new PeriodFormatterBuilder()
      .printZeroRarelyLast()
      .appendHours()
      .appendSuffix(" hour", " hours")
      .appendSeparator(", ", " and ")
      .appendMinutes()
      .appendSuffix(" minute", " minutes")
      .appendSeparator(", ", " and ")
      .appendSecondsWithOptionalMillis()
      .appendSuffix(" second", " seconds")
      .toFormatter
    period.toString(formatter)
  }
}

/**
 * Provides a singleton StatusService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{StatusServiceActorRefProvider}}}.
 */
trait StatusServiceProvider {
  this: StatusServiceActorRefProvider =>

  val statusService: Singleton[StatusService] =
    Singleton(() => new StatusService(statusServiceActorRef())).bindToSet(AllServices)
}

trait StatusServiceProviderx {
  this: StatusServiceActorRefProvider
    with ServiceComposer =>

  final val statusService: Singleton[StatusService] =
    Singleton(() => new StatusService(statusServiceActorRef()))

  addService(statusService)
}
