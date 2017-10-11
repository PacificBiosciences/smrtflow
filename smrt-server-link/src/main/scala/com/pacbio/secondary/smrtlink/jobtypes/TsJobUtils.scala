package com.pacbio.secondary.smrtlink.jobtypes

import java.net.URL
import java.nio.file.Path

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.analysis.jobs.JobResultsWriter
import com.pacbio.secondary.smrtlink.client.EventServerClient
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by mkocher on 10/11/17.
  */
trait TsJobUtils extends LazyLogging {
  // put this into Utils

  val NOT_CONFIGURED_MESSAGE =
    "System is not configured with External EVE server URL."

  // This is pretty painful to create a new actor system and shut it down for this client useage
  def upload(eveUrl: URL,
             apiSecret: String,
             tgz: Path,
             writer: JobResultsWriter): Future[String] = {

    val system = ActorSystem("client-upload")
    val client = new EventServerClient(eveUrl, apiSecret)(system)

    val startMsg = s"Client ${client.toUploadUrl} Attempting to upload $tgz"
    logger.info(startMsg)
    writer.writeLine(startMsg)

    val f = client.upload(tgz).map { event =>
      val msg = s"Successfully uploaded $tgz. Created Event ${event.uuid}"
      logger.info(msg)
      writer.writeLine(msg)
      msg
    }

    f.onComplete(_ => system.shutdown())

    f

  }

}
