package com.pacbio.secondary.smrtlink.actors

import java.util.UUID
import java.net.URL

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
                        dnsName: Option[URL],
                        externalConfig: Option[ExternalEventServerConfig])
    extends Actor with LazyLogging with SmrtLinkJsonProtocols{

  import EventManagerActor._

  val client: Option[EventServerClient] = externalConfig.map(x => new EventServerClient(x.host, x.port)(context.system))

  context.system.scheduler.scheduleOnce(10.seconds, self, CheckExternalServerStatus)


  override def preStart() = {
    logger.info(s"Starting $self with smrtLinkID $smrtLinkId and External Event sServer config $externalConfig")
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
      val systemEvent = toSystemEvent(e)
      sender ! systemEvent
      sendSystemEvent(systemEvent)

    case e: EulaRecord =>
      val event = SmrtLinkEvent(EventTypes.EULA_ACCEPTED, 1, UUID.randomUUID(), JodaDateTime.now(), e.toJson.asJsObject)
      val systemEvent = toSystemEvent(event)
      logger.info(s"EventManager $systemEvent")
      sendSystemEvent(systemEvent)

    case x => logger.debug(s"Event Manager got unknown handled message $x")
  }
}

trait EventManagerActorProvider {
  this: ActorRefFactoryProvider with SmrtLinkConfigProvider =>

  val eventManagerActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[EventManagerActor], Constants.SERVER_UUID, dnsName(), externalEventHost()), "EventManagerActor"))
}
