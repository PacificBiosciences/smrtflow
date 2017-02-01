package com.pacbio.common.actors.alarms

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors.ActorSystemProvider
import com.pacbio.common.dependency.Singleton
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait AlarmComposer {
  this: ActorSystemProvider =>

  val _alarms = ArrayBuffer.empty[Singleton[ActorRef]]

  def addAlarm(alarm: Singleton[ActorRef]) = _alarms += alarm

  def alarms(): Set[ActorRef] = _alarms.map(_()).toSet

  def initAlarms: Set[String] = {
    import AlarmActor._

    implicit val timeout = Timeout(10.seconds)

    val f = Future.sequence(alarms().map { a =>
      for {
        am <- a ? INIT
        n  <- (a ? NAME).mapTo[String]
        _  <- Future { QuartzSchedulerExtension(actorSystem()).schedule(n, a, UPDATE) }
      } yield n
    })

    Await.result(f, Duration.Inf)
  }
}
