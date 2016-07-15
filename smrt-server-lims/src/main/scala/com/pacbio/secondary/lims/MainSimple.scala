package com.pacbio.secondary.lims

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import spray.can.Http


/**
 * Entry point for the LIMS service
 *
 * Entry point for the server that is as stripped-down as possible. The idea was to build up from
 * the minimum and see what is needed.
 */
object MainSimple extends App with ConfigLoader {

  LoggerOptions.parseAddDebug(args)

  implicit val system = ActorSystem("internal-smrt-link-system")

  // use Akka to create our Spray Service
  val service = system.actorOf(Props[InternalServiceActor], "internal-smrt-link-service")

  IO(Http) ! Http.Bind(service, conf.getString("smrt-server-lims.host"), conf.getInt("smrt-server-lims.port"))
}