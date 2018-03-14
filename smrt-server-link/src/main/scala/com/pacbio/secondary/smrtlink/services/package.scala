package com.pacbio.secondary.smrtlink

import java.io.{PrintWriter, StringWriter}

import com.pacbio.secondary.smrtlink.models._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{
  HttpOrigin,
  HttpOriginRange,
  `Access-Control-Allow-Origin`
}
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry}
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import spray.json._

import scala.util.control.NonFatal

package object services {

  import akka.actor.{Actor, ActorLogging}
  import StatusCodes._

  /**
    * This entire model needs to be rethought to avoid duplication with
    * the rejection and exception handling spray model.
    */
  object PacBioServiceErrors {

    /**
      * Should this extend RejectionWithOptionalCause ?
      *
      * This looks like we're reinventing the wheel here.
      *
      * @param message Display Message
      * @param cause Optional detail Throwable
      */
    sealed abstract class PacBioServiceError(message: String,
                                             cause: Throwable = null)
        extends Exception(message, cause) {

      val code: StatusCode
      def response: ThrowableResponse = {
        val stackTrace = Option(cause)
          .map { t: Throwable =>
            val sw = new StringWriter
            t.printStackTrace(new PrintWriter(sw))
            s"\n$sw"
          }
          .getOrElse("")
        ThrowableResponse(code.intValue, message + stackTrace, code.reason)
      }
    }

    case class ResourceNotFoundError(message: String, cause: Throwable = null)
        extends PacBioServiceError(message, cause) {
      override val code = NotFound
    }
    case class MethodNotImplementedError(message: String,
                                         cause: Throwable = null)
        extends PacBioServiceError(message, cause) {
      override val code = NotImplemented
    }
    case class ConflictError(message: String, cause: Throwable = null)
        extends PacBioServiceError(message, cause) {
      override val code = Conflict
    }
    case class UnprocessableEntityError(message: String,
                                        cause: Throwable = null)
        extends PacBioServiceError(message, cause) {
      override val code = UnprocessableEntity
    }
    case class UnknownServerError(message: String, cause: Throwable = null)
        extends PacBioServiceError(message, cause) {
      override val code = InternalServerError
    }
  }

  trait PacBioServiceErrors extends LazyLogging {

    import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols.pbThrowableResponseFormat
    import PacBioServiceErrors._
    import SprayJsonSupport._

    private def addAccessControlHeader(
        headers: List[HttpHeader]): List[HttpHeader] =
      if (headers.forall(!_.isInstanceOf[`Access-Control-Allow-Origin`]))
        `Access-Control-Allow-Origin`(HttpOrigin("*")) :: headers
      else
        headers

    private def toErrorAndLog(
        statusCode: StatusCode,
        message: String): (StatusCode, ThrowableResponse) = {
      val throwAble =
        ThrowableResponse(statusCode.intValue(), message, statusCode.reason())
      logger.error(throwAble.toLogMessage())
      (statusCode, throwAble)
    }

    private def toThrowAbleAndLog[T <: PacBioServiceError](
        ex: T): (StatusCode, ThrowableResponse) = {
      val throwAble = ex.response
      logger.error(throwAble.toLogMessage())
      (ex.code, throwAble)
    }

    /**
      * Custom Rejection handler to map errors to proper PacBio Error
      * model and http codes
      *
      * The docs suggest avoid bundling several cases into a single handle call
      *
      * https://doc.akka.io/docs/akka-http/current/routing-dsl/rejections.html
      *
      * @return
      */
    def pacBioRejectionHandler(): RejectionHandler =
      RejectionHandler
        .newBuilder()
        .handle {
          case AuthenticationFailedRejection(cause, challengeHeaders) =>
            complete(toErrorAndLog(Unauthorized,
                                   s"Request is rejected with cause: $cause"))
        }
        .handle {
          case ex: Rejection => //FIXME.
            complete(
              toErrorAndLog(StatusCodes.InternalServerError, ex.toString))
        }
        .handleNotFound { ctx =>
          ctx.complete(
            toErrorAndLog(NotFound,
                          s"Route: ${ctx.request.uri} does not exist."))
        }
        .result()

    def pacbioExceptionHandler: ExceptionHandler =
      ExceptionHandler {
        case _: ArithmeticException =>
          extractUri { uri =>
            complete(
              toErrorAndLog(UnprocessableEntity,
                            UnprocessableEntity.defaultMessage))
          }
        case ex: IllegalArgumentException =>
          complete(toErrorAndLog(NotFound, ex.getMessage))
        case ex: PacBioServiceError =>
          complete(toThrowAbleAndLog(ex))
      }
  }

  trait PacBioService extends Directives with ServiceIdUtils {

    val manifest: PacBioComponentManifest
    def routes: Route

    // This is where this should be customized
    val corsSettings = CorsSettings.defaultSettings

    def prefixedRoutes = cors(corsSettings) { routes }
  }

  trait ServiceIdUtils {

    private def toServiceId(n: PacBioNamespaces.PacBioNamespace, s: String) =
      s"pacbio.${n.name}.${s.toLowerCase()}"

    /**
      * Util for creating Pacbio Tool ID type (e.g., pacbio.services.my_id, pacbio.services.secondary.internal_dataset)
      *
      * Subsystems resources have 3 types of components, Tools (e.g., blasr),
      * Services, SMRTApps (angular). SMRT Apps can depend on Services and SMRT Apps, Services can depend on other
      * Services and Tools. Tools can only depend on other Tools.
      *
      * @param s base Id name
      * @return
      */
    def toToolId(s: String): String =
      toServiceId(PacBioNamespaces.SMRTTools, s)

    def toAppId(s: String): String = toServiceId(PacBioNamespaces.SMRTApps, s)

    def toServiceId(s: String): String =
      toServiceId(PacBioNamespaces.SMRTServices, s)
  }
}
