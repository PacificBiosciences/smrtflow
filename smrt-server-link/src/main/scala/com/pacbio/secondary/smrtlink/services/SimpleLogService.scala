package com.pacbio.secondary.smrtlink.services

import scala.concurrent._
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models.{
  LogLevels,
  LogMessageRecord,
  PacBioComponentManifest
}

class SimpleLogService
    extends SmrtLinkBaseMicroService
    with StatusCodeJoiners
    with Directives {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val ROUTE_PREFIX = "loggers"

  val manifest = PacBioComponentManifest(toServiceId("smrtlink.loggers"),
                                         "SMRT Link General Log Service",
                                         "0.1.0",
                                         "SMRT Link Log Service")

  override val routes =
    pathPrefix(ROUTE_PREFIX) {
      pathEndOrSingleSlash {
        post {
          entity(as[LogMessageRecord]) { msg =>
            complete {
              created {
                val msgString = s"sourceId:${msg.sourceId} ${msg.message}"
                val response = "message logged"
                msg.level match {
                  case LogLevels.TRACE => logger.trace(msgString)
                  case LogLevels.DEBUG => logger.debug(msgString)
                  case LogLevels.INFO => logger.info(msgString)
                  case LogLevels.WARN => logger.warn(msgString)
                  case LogLevels.ERROR => logger.error(msgString)
                  case LogLevels.CRITICAL => logger.error(msgString)
                  case LogLevels.FATAL => logger.error(msgString)
                }
                MessageResponse(response)
              }
            }
          }
        }
      }
    }
}

trait SimpleLogServiceProvider { this: ServiceComposer =>

  val simpleLogService: Singleton[SimpleLogService] =
    Singleton(() => new SimpleLogService())

  addService(simpleLogService)
}
