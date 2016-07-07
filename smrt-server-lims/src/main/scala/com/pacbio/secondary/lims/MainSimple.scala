package com.pacbio.secondary.lims

import java.net.BindException

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import com.pacbio.logging.LoggerOptions
import spray.can.Http

import scala.concurrent.{Await, Future}
import scala.util.control.ControlThrowable

/**
 * Entry point for the LIMS service
 *
 * Entry point for the server that is as stripped-down as possible. The idea was to build up from
 * the minimum and see what is needed.
 */
object MainSimple extends App {

  LoggerOptions.parseAddDebug(args)

  implicit val system = ActorSystem("internal-smrt-link-system")

  /* Use Akka to create our Spray Service */
  val service = system.actorOf(Props[InternalServiceActor], "internal-smrt-link-service")

  /* and bind to Akka's I/O interface */
  IO(Http) ! Http.Bind(service, "127.0.0.1", 8070)
}




