package com.pacbio.secondary.smrtlink.services

import scala.concurrent._
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.models.{ClientLogMessage, PacBioComponentManifest, SmrtLinkJsonProtocols, LogLevel}


class SimpleLogService
  extends SmrtLinkBaseMicroService
  with StatusCodeJoiners
  with Directives {

  import SmrtLinkJsonProtocols._

  val ROUTE_PREFIX = "loggers"

  val manifest = PacBioComponentManifest(
    toServiceId("smrtlink.loggers"),
    "SMRT Link DataSetService Service",
    "0.1.0",
    "SMRT Link Log Service")

  override val routes =
    pathPrefix(ROUTE_PREFIX) {
      pathEndOrSingleSlash {
        post {
          entity(as[ClientLogMessage]) { msg =>
            complete {
              created {
                val msgString = s"sourceId:${msg.sourceId} ${msg.message}"
                var response = "message logged"
                msg.level match {
                  case LogLevel.TRACE    => logger.trace(msgString)
                  case LogLevel.DEBUG    => logger.debug(msgString)
                  case LogLevel.INFO     => logger.info(msgString)
                  case LogLevel.WARN     => logger.warn(msgString)
                  case LogLevel.ERROR    => logger.error(msgString)
                  case LogLevel.CRITICAL => logger.error(msgString)
                  case LogLevel.FATAL    => logger.error(msgString)
                }
                MessageResponse(response)
              }
            }
          }
        }
      }
    }
}

trait SimpleLogServiceProvider {
  this: ServiceComposer =>

  val simpleLogService: Singleton[SimpleLogService] =
    Singleton(() => new SimpleLogService())

  addService(simpleLogService)
}
