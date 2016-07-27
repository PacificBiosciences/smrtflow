package com.pacbio.secondary.lims

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http


/**
 * Entry point for the LIMS service
 *
 * Entry point for the server that is as stripped-down as possible. The idea was to build up from
 * the minimum and see what is needed.
 */
object MainSimple extends App with ConfigLoader with LazyLogging{

  LoggerOptions.parseAddDebug(args)
  logger.debug(s"Starting smrt-lims with arguments: ${args.mkString(" ")}")

  implicit val system = ActorSystem("internal-smrt-link-system")

  // use Akka to create our Spray Service
  val service = system.actorOf(Props[InternalServiceActor], "internal-smrt-link-service")

  val host = conf.getString("pb-services.host")
  val port = conf.getInt("pb-services.port")
  logger.debug(s"Binding: $host:$port")
  IO(Http) ! Http.Bind(service, host, port)
}