package com.pacbio.secondary.smrtlink

import java.io.{PrintWriter, StringWriter}

import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.utils.CORSSupport
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{
  HttpOrigin,
  `Access-Control-Allow-Origin`
}
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry}
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import scala.util.control.NonFatal

package object services {

  import akka.actor.{Actor, ActorLogging}
  import StatusCodes._

  //import spray.http.StatusCode
  //import spray.http.StatusCodes._
  // import akka.http.scaladsl.server._

  trait AppConfig {

    // TODO(smcclellan): The role of this model needs to be clarified
    // TODO(smcclellan): Use ConfigProvider?
    // TODO(smcclellan): Use ClockProvider?
    lazy val conf: SubsystemConfig = {
      // Load conf from application.conf
      val conf = ConfigFactory.load()
      SubsystemConfig(conf.getString("smrt-server.name"),
                      conf.getString("smrt-server.display-name"),
                      JodaDateTime.now)
    }
  }

  object PacBioServiceErrors {
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

    def response(e: Throwable): ThrowableResponse = e match {
      case t: PacBioServiceError => t.response
      case NonFatal(t) => new UnknownServerError(t.getMessage, t).response
    }

//    val responseMarshaller: ToResponseMarshaller[(Int, ThrowableResponse)] =
//      fromStatusCodeAndT(
//        (s: Int) => StatusCodes.getForKey(s).getOrElse(InternalServerError),
//        sprayJsonMarshallerConverter(pbThrowableResponseFormat))

    def addAccessControlHeader(headers: List[HttpHeader]): List[HttpHeader] =
      if (headers.forall(!_.isInstanceOf[`Access-Control-Allow-Origin`]))
        `Access-Control-Allow-Origin`(HttpOrigin("*")) :: headers
      else
        headers

    def mapErrorToRootObject(message: String): String = ""

    def pacBioRejectionHandler(): RejectionHandler =
      RejectionHandler
        .newBuilder()
        .handle {
          case AuthenticationFailedRejection(cause, challengeHeaders) =>
            logger.error(s"Request is rejected with cause: $cause")
            complete((Unauthorized, mapErrorToRootObject("message")))
        }
        .handleNotFound { ctx =>
          val emsg = s"Route: ${ctx.request.uri} does not exist."
          logger.error(emsg)
          ctx.complete((NotFound, mapErrorToRootObject(emsg)))
        }
        .result()
        .withFallBack(RejectionHandler.default)

    implicit def pacbioExceptionHandler: ExceptionHandler =
      ExceptionHandler {
        case _: ArithmeticException =>
          extractUri { uri =>
            println(s"Request to $uri could not be handled normally")
            complete(
              HttpResponse(InternalServerError,
                           entity = "Bad numbers, bad result!!!"))
          }
        case ResourceNotFoundError(message, cause) =>
          complete(
            HttpResponse(InternalServerError,
                         entity = "Bad numbers, bad result!!!"))
      }

//    implicit val pacbioExceptionHandler: ExceptionHandler = ExceptionHandler {
//
//      case _: ArithmeticException =>
//        extractUri { uri =>
//          println(s"Request to $uri could not be handled normally")
//          complete(HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!"))
//
//      case NonFatal(e) =>
//        ctx =>
//          val resp = response(e)
//          val message = if (resp.message == null) {
//            e.getStackTrace.mkString("\n  ")
//          } else {
//            resp.message
//          }
//          logger.warn(
//            s"Non-fatal exception: ${resp.httpCode} - ${resp.errorType} - $message Request ${ctx.request}")
//          ctx
//          // Exception handling bypasses CORSSupport, so we add the header here
//            .withHttpResponseHeadersMapped(addAccessControlHeader)
//            .complete(resp.httpCode, resp)(responseMarshaller)
//    }

//    implicit val pacbioRejectionHandler: RejectionHandler =
//      RejectionHandler.Default.andThen { route =>
//        route.compose[RequestContext] { rc =>
//          rc.withHttpResponseHeadersMapped(addAccessControlHeader)
//            .withHttpResponseMapped { resp =>
//              if (resp.status.isFailure) {
//                logger.warn(
//                  s"Request rejected: ${resp.status.intValue} - ${resp.status.reason} - ${resp.entity.asString}")
//                val tResp = ThrowableResponse(resp.status.intValue,
//                                              resp.entity.asString,
//                                              resp.status.reason)
//                val bResp =
//                  pbThrowableResponseFormat.write(tResp).prettyPrint.getBytes
//                resp.copy(
//                  entity = HttpEntity(ContentTypes.`application/json`, bResp))
//              } else {
//                resp
//              }
//            }
//        }
//      }
  }

  trait PacBioService extends Directives with ServiceIdUtils {

    val manifest: PacBioComponentManifest
    def routes: Route

    def prefixedRoutes = cors() { routes }
  }

//  class RoutedHttpService(route: Route)
//      extends Actor
//      with ActorLogging
//      with PacBioServiceErrors {
//
//    import CORSSupport._
//    import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols.pbThrowableResponseFormat
//
//    implicit def actorRefFactory = context
//
//    def showRequest(req: HttpRequest): LogEntry = {
//
//      var asString: String = req.entity.toOption
//        .map(
//          data => s"request: ${req.method} ${req.uri} ${data}"
//        )
//        .getOrElse(s"request: ${req.method} ${req.uri}")
//      LogEntry(asString, akka.event.Logging.InfoLevel)
//    }
//
//    def receive: Receive =
//      runRoute(compressResponseIfRequested() {
//        DebuggingDirectives.logRequest(showRequest _) {
//          route
//        }
//      })(pacbioExceptionHandler,
//         pacbioRejectionHandler,
//         context,
//         RoutingSettings.default,
//         LoggingContext.fromActorRefFactory)
//
//    override def timeoutRoute = complete {
//      val tResp = ThrowableResponse(
//        InternalServerError.intValue,
//        "The server was not able to produce a timely response to your request.",
//        InternalServerError.reason)
//
//      val bResp: Array[Byte] =
//        pbThrowableResponseFormat.write(tResp).prettyPrint.getBytes
//
//      HttpResponse(
//        InternalServerError,
//        entity = HttpEntity(ContentTypes.`application/json`, bResp),
//        headers = List(allowOriginHeader)
//      )
//    }
//  }

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
