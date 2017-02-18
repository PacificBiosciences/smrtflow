package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import akka.actor.{Actor, ActorRef, Props}

import spray.json._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.{EulaRecord, SmrtLinkJsonProtocols, SmrtLinkEvent, SmrtLinkSystemEvent}


class EventManagerActor extends Actor with LazyLogging with SmrtLinkJsonProtocols{

  val smrtLinkId = UUID.randomUUID()

  def toSystemEvent(e: SmrtLinkEvent) =
    SmrtLinkSystemEvent(smrtLinkId, e.eventTypeId, e.uuid, e.createdAt, e.message)

  override def receive: Receive = {

    case e: EulaRecord =>
      // maybe these eventTypeId(s) should be bolted back on the case class to centralize?
      val event = SmrtLinkEvent("smrtlink_eula_accepted", UUID.randomUUID(), JodaDateTime.now(), e.toJson.asJsObject)
      val systemEvent = toSystemEvent(event)
      logger.info(s"EventManager $systemEvent")


    case x => logger.debug(s"Event Manager got unknown handled message $x")
  }
}

trait EventManagerActorProvider {
  this: ActorRefFactoryProvider =>

  val eventManagerActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[EventManagerActor]), "EventManagerActor"))
}
