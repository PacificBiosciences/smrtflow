package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{Actor, ActorRef, Props}
import spray.json._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{Constants, ServiceStatus}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.client.EventServerClient
import com.pacbio.secondary.smrtlink.models._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.util.control.NonFatal

object EventManagerActor {
  case object CheckExternalServerStatus
  case class CreateEvent(event: SmrtLinkEvent)
}


class EventManagerActor(smrtLinkId: UUID,
                        dnsName: Option[String],
                        externalConfig: Option[ExternalEventServerConfig])
    extends Actor with LazyLogging with SmrtLinkJsonProtocols{

  import EventManagerActor._

  // This state will be updated when/if a "Eula" message is sent. This will enable/disable sending messages
  // to the external server.
  var enableExternalMessages = false

  // If the system is not configured with an event server. No external messages will ever be sent
  val client: Option[EventServerClient] = externalConfig.map(x => new EventServerClient(x.host, x.port)(context.system))

  context.system.scheduler.scheduleOnce(10.seconds, self, CheckExternalServerStatus)


  override def preStart() = {
    val dns = dnsName.map(n => s"dns name $n").getOrElse("")
    logger.info(s"Starting $self with smrtLinkID $smrtLinkId $dns and External Event Server config $externalConfig")
    logger.info("DNS name: " + dnsName.getOrElse("NONE"))
  }

  def checkExternalServerStatus(): Option[Future[String]] = {
    client.map(c => c.getStatus.map { status => s"External Server $c status $status"
    }.recover( {case NonFatal(ex) => s"Failed to connect to External Event Server $c ${ex.getMessage}"})
        .map {statusMessage =>
          logger.info(statusMessage)
          statusMessage
        }
    )
  }

  def toSystemEvent(e: SmrtLinkEvent) =
    SmrtLinkSystemEvent(smrtLinkId, e.eventTypeId, e.eventTypeVersion, e.uuid, e.createdAt, e.message, dnsName)

  private def sendSystemEvent(e: SmrtLinkSystemEvent): Unit = {
    Try {client.map(c => c.sendSmrtLinkSystemEvent(e))}
  }

  override def receive: Receive = {

    case CheckExternalServerStatus =>
      checkExternalServerStatus()

    case CreateEvent(e) =>
      if (enableExternalMessages) {
        val systemEvent = toSystemEvent(e)
        sender ! systemEvent
        sendSystemEvent(systemEvent)
      }

    case e: EulaRecord =>
      // This has some legacy/historical cruft. This is no longer a "EULA". It's an notification message
      // for tech support to schedule a Instrument Upgrade

      enableExternalMessages = e.enableInstallMetrics

      if (e.enableInstallMetrics) {
        val event = SmrtLinkEvent(EventTypes.INST_UPGRADE_NOTIFICATION, 1, UUID.randomUUID(), JodaDateTime.now(), e.toJson.asJsObject)
        val systemEvent = toSystemEvent(event)
        logger.info(s"EventManager $systemEvent")
        sendSystemEvent(systemEvent)
      } else {
        logger.warn(s"Eula installMetrics is false. Skipping sending to external server. $e")
      }

    case x => logger.debug(s"Event Manager got unknown handled message $x")
  }
}

trait EventManagerActorProvider {
  this: ActorRefFactoryProvider with SmrtLinkConfigProvider =>

  val eventManagerActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[EventManagerActor], Constants.SERVER_UUID, dnsName(), externalEventHost()), "EventManagerActor"))
}
