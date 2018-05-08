package com.pacbio.secondary.smrtlink.actors

import java.net.URL
import java.nio.file.Path
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{Actor, ActorRef, Props}
import spray.json._
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.client.EventServerClient
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object EventManagerActor {
  case object CheckExternalServerStatus
  case class CreateEvent(event: SmrtLinkEvent)
  case class EnableExternalMessages(enable: Boolean)
  case class UploadTechSupportTgz(path: Path)
}

class EventManagerActor(smrtLinkId: UUID,
                        dnsName: Option[String],
                        externalEveUrl: Option[URL],
                        apiSecret: String)
    extends Actor
    with LazyLogging
    with SmrtLinkJsonProtocols {

  import EventManagerActor._

  // This state will be updated when/if a "Eula" message is sent. This will enable/disable sending messages
  // to the external server.
  var enableExternalMessages = false

  // If the system is not configured with an event server. No external messages will ever be sent
  val client: Option[EventServerClient] =
    externalEveUrl.map(x =>
      new EventServerClient(x, apiSecret)(context.system))

  context.system.scheduler
    .scheduleOnce(10.seconds, self, CheckExternalServerStatus)

  override def preStart() = {
    val dns = dnsName.map(n => s"dns name $n").getOrElse("")
    logger.info(
      s"Starting $self with smrtLinkID $smrtLinkId $dns and External Event Server URL $externalEveUrl with enableExternalEvents=$enableExternalMessages")
    logger.info("DNS name: " + dnsName.getOrElse("NONE"))
  }

  override def postRestart(reason: Throwable) {
    super.postRestart(reason)
    // This can potentially be a problem. The enableExternalMessages state
    // can be out of sync
    logger.warn(s"Restarted $self because of ${reason.getMessage}")
  }

  def checkExternalServerStatus(): Option[Future[String]] = {
    client.map(c =>
      c.getStatus
        .map { status =>
          s"External Server $c status $status"
        }
        .recover({
          case NonFatal(ex) =>
            s"Failed to connect to External Event Server $c at ${c.RootUri} ${ex.getMessage}"
        })
        .map { statusMessage =>
          logger.info(statusMessage)
          statusMessage
      })
  }

  def toSystemEvent(e: SmrtLinkEvent) =
    SmrtLinkSystemEvent(smrtLinkId,
                        e.eventTypeId,
                        e.eventTypeVersion,
                        e.uuid,
                        e.createdAt,
                        e.message,
                        dnsName)

  private def sendSystemEvent(e: SmrtLinkSystemEvent): Unit = {

    def eventSender(c: EventServerClient): Future[String] = {
      c.sendSmrtLinkSystemWithRetry(e)
        .map(ev =>
          s"Successfully Sent Event ${ev.uuid} type:${ev.eventTypeId}")
        .recoverWith {
          case NonFatal(ex) =>
            Future.successful(
              s"Failed to send event ${e.uuid} type:${e.eventTypeId} ${ex.getMessage}")
        }
    }

    val fx: Future[String] = client
      .map { c =>
        logger.info(
          s"Attempting to send message to external Server ${e.uuid} ${e.eventTypeId}")
        eventSender(c)
      }
      .getOrElse(Future.successful(
        s"System is Not configured to Send Event (enableExternalMessages=$enableExternalMessages). Can not send event ${e.uuid} ${e.eventTypeId}"))

    fx onComplete {
      case Success(msg) => logger.info(msg)
      case Failure(ex) =>
        logger.warn(s"Failed to send external event to Eve. ${ex.getMessage}")

    }

  }

  // Should this spawn a "worker" actor to run this call?
  private def upload(c: EventServerClient,
                     tgz: Path): Future[SmrtLinkSystemEvent] = {
    logger.info(s"Client ${c.UPLOAD_URI} Attempting to upload $tgz")
    c.upload(tgz)
  }

  def uploadIfConfigured(path: Path): Unit = {
    client match {
      case Some(c) =>
        upload(c, path)
      case _ =>
        logger.info("System is not configured to upload files")
    }
  }

  override def receive: Receive = {

    case CheckExternalServerStatus =>
      checkExternalServerStatus()

    case EnableExternalMessages(enable) =>
      // Unless the system is Configured with a Eve URL, this has no impact
      enableExternalMessages = enable
      val msx = client
        .map(c => s"Enabled External Messages to ${c.RootUri}")
        .getOrElse(
          "System is not configured with an Eve URL. Can not send message")

      sender ! msx

    case CreateEvent(e) =>
      if (enableExternalMessages) {
        val systemEvent = toSystemEvent(e)
        sender ! systemEvent
        sendSystemEvent(systemEvent)
      } else {
        // This should send a failed message back
        logger.warn(
          s"External Event Sending is disabled. Unable to send event ${e.uuid} ${e.eventTypeId}")
      }

    case e: EulaRecord =>
      // This has some legacy/historical cruft. This is no longer a "EULA". It's an notification message
      // for tech support to schedule a Instrument Upgrade

      enableExternalMessages = e.enableInstallMetrics
      val event = SmrtLinkEvent(EventTypes.INST_UPGRADE_NOTIFICATION,
                                1,
                                UUID.randomUUID(),
                                JodaDateTime.now(),
                                e.toJson.asJsObject)
      val systemEvent = toSystemEvent(event)

      if (e.enableInstallMetrics) {
        sendSystemEvent(systemEvent)
        sender ! systemEvent
      } else {
        logger.warn(
          s"Eula installMetrics is false. Skipping sending to external server. $e")
        // This is to have a consistent interface, but this is making it a bit unclear that
        // the message isn't sent. Should clarify this interface
        sender ! systemEvent
      }

    case UploadTechSupportTgz(path) =>
      uploadIfConfigured(path)

    case x => logger.debug(s"Event Manager got unknown handled message $x")
  }
}

trait EventManagerActorProvider {
  this: ActorRefFactoryProvider with SmrtLinkConfigProvider =>

  val eventManagerActor: Singleton[ActorRef] =
    Singleton(
      () =>
        actorRefFactory().actorOf(
          Props(classOf[EventManagerActor],
                serverId(),
                dnsName(),
                externalEveUrl(),
                apiSecret()),
          "EventManagerActor"
      )
    )
}
