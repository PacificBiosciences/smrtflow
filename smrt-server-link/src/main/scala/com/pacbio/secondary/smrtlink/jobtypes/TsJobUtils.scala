package com.pacbio.secondary.smrtlink.jobtypes

import java.net.URL
import java.nio.file.Path

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.analysis.jobs.JobResultsWriter
import com.pacbio.secondary.smrtlink.client.EventServerClient
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by mkocher on 10/11/17.
  */
trait TsJobUtils extends LazyLogging {

  // These are defined as defs, so they can be mixed into ServiceJobOptions

  // This should be configurable from the SystemJobConfig
  def DEFAULT_MAX_UPLOAD_TIME = 5.minutes

  private def notConfigured(msg: String) =
    s"System is not configured $msg. Unable to create or upload TGZ Bundle"

  def NOT_CONFIGURED_MESSAGE_EVE =
    notConfigured("with External EVE server URL")

  def NOT_CONFIGURED_MESSAGE_SL_ROOT =
    notConfigured("with SMRT Link System Root")

  def validateEveUrl(eveURL: Option[URL]): Future[URL] = {
    eveURL match {
      case Some(u) => Future.successful(u)
      case _ =>
        Future.failed(new UnprocessableEntityError(
          "External EVE URL is not configured in System. Unable to send message to TechSupport"))
    }
  }

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
