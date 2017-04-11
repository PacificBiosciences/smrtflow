package com.pacbio.secondary.smrtlink.client

import java.net.URL

import akka.actor.ActorSystem
import com.pacbio.common.client.Retrying

/**
  * Base Client trait for
  *
  */
trait ClientBase extends Retrying{
  implicit val actorSystem: ActorSystem
  val baseUrl: URL

  // This should really return a URL instance, not a string
  def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString

}
