package com.pacbio.secondary.smrtlink.actors

import java.net.URL
import java.nio.file.Path
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{Actor, ActorRef, Props}
import spray.json._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models.Constants
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.client.EventServerClient
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.mail.PbMailer
import com.pacbio.secondary.smrtlink.models._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.util.control.NonFatal

object EventManagerActor {
  case object CheckExternalServerStatus
  case class CreateEvent(event: SmrtLinkEvent)
  // Upload a TGZ file
  case class UploadTgz(path: Path)
  case class EnableExternalMessages(enable: Boolean)
}

class EventManagerActor(smrtLinkId: UUID,
                        dnsName: Option[String],
                        externalEveUrl: Option[URL],
                        apiSecret: String,
                        smrtLinkUiPort: Int,
                        mailHost: Option[String] = None,
                        mailPort: Int = 25,
                        mailUser: Option[String] = None,
                        mailPassword: Option[String] = None)
    extends Actor
    with LazyLogging
    with SmrtLinkJsonProtocols
    with PbMailer {

  import EventManagerActor._

  // If the system is not configured with the DNS name, no emails will be sent.
  val uiJobsUrl =
    dnsName.map(d => new URL(s"https://$d:$smrtLinkUiPort/sl/#/analysis/job"))

  // This state will be updated when/if a "Eula" message is sent. This will enable/disable sending messages
  // to the external server.
  var enableExternalMessages = false

  // If the system is not configured with an event server. No external messages will ever be sent
  val client: Option[EventServerClient] = externalEveUrl.map(x =>
    new EventServerClient(x, apiSecret)(context.system))

  context.system.scheduler
    .scheduleOnce(10.seconds, self, CheckExternalServerStatus)

  override def preStart() = {
    val dns = dnsName.map(n => s"dns name $n").getOrElse("")
    logger.info(
      s"Starting $self with smrtLinkID $smrtLinkId $dns and External Event Server URL $externalEveUrl")
    logger.info("DNS name: " + dnsName.getOrElse("NONE"))
  }

  def checkExternalServerStatus(): Option[Future[String]] = {
    client.map(c =>
      c.getStatus
        .map { status =>
          s"External Server $c status $status"
        }
        .recover({
          case NonFatal(ex) =>
            s"Failed to connect to External Event Server $c at ${c.baseUrl} ${ex.getMessage}"
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
    Try {
      client.map { c =>
        logger.info(s"Attempting to send message to external Server $e")
        c.sendSmrtLinkSystemEvent(e)
      }
    }
  }

  // Should this spawn a "worker" actor to run this call?
  private def upload(c: EventServerClient,
                     tgz: Path): Future[SmrtLinkSystemEvent] = {
    logger.info(s"Client ${c.toUploadUrl} Attempting to upload $tgz")

    val f = c.upload(tgz)

    f.onSuccess {
      case e: SmrtLinkSystemEvent =>
        logger.info(s"Upload successful. Event $e")
    }
    f.onFailure {
      case NonFatal(e) =>
        logger.error(s"Failed to upload $tgz Error ${e.getMessage}")
    }

    f
  }

  override def receive: Receive = {

    case CheckExternalServerStatus =>
      checkExternalServerStatus()

    case EnableExternalMessages(enable) =>
      enableExternalMessages = enable

    case CreateEvent(e) =>
      if (enableExternalMessages) {
        val systemEvent = toSystemEvent(e)
        sender ! systemEvent
        sendSystemEvent(systemEvent)
      } else {
        logger.warn("Enabling external message sending id disabled.")
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

    case UploadTgz(tgzPath) =>
      logger.info(s"Triggering upload of $tgzPath")
      client match {
        case Some(c) =>
          upload(c, tgzPath)
        case _ =>
          logger.warn(
            "Unable to upload. System is not configured with a external server URL")
      }

    case JobCompletedMessage(job) =>
      // This is a fire and forget
      uiJobsUrl match {
        case Some(jobsBaseUrl) =>
          sendEmail(job,
                    jobsBaseUrl,
                    mailHost,
                    mailPort,
                    mailUser,
                    mailPassword)
        case _ =>
          logger.debug(
            s"System is not configured to send email. Must provide DNS name in SMRT Link System Config")
      }

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
                apiSecret(),
                smrtLinkUiPort(),
                mailHost(),
                mailPort(),
                mailUser(),
                mailPassword()),
          "EventManagerActor"
      ))
}
