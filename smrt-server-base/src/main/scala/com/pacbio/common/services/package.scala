package com.pacbio.common

import java.io.{PrintWriter, StringWriter}

import com.pacbio.common.models._
import com.pacbio.common.services.utils.CORSSupport
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import spray.http._
import spray.http.HttpHeaders.`Access-Control-Allow-Origin`
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling._
import spray.routing.directives.LogEntry
import spray.util.LoggingContext

import scala.util.control.NonFatal

package object services {

  import akka.actor.{ActorLogging, Actor}
  import spray.http.StatusCodes._
  import spray.http.StatusCode
  import spray.routing._

  trait AppConfig {

    // TODO(smcclellan): The role of this model needs to be clarified
    // TODO(smcclellan): Use ConfigProvider?
    // TODO(smcclellan): Use ClockProvider?
    lazy val conf: SubsystemConfig = {
      // Load conf from application.conf
      val conf = ConfigFactory.load()
      SubsystemConfig(conf.getString("smrt-server.name"), conf.getString("smrt-server.display-name"), JodaDateTime.now)
    }
  }

  object StatusCodeJoiners extends BasicMarshallers with BasicToResponseMarshallers {
    import ToResponseMarshallable._

    /**
     * Bundles a service response with an HTTP code. This function should generally not be used directly, but instead
     * the {{{ok}}}, {{{created}}}, etc. functions should be used like so:
     *
     * {{{
     *   class MyService extends PacBioService {
     *     val routes: Route =
     *       path("resource") {
     *         get {
     *           complete {
     *             ok {
     *               (GetResource ? actor).mapTo[Try[Resource]]
     *             }
     *           }
     *         } ~
     *         post {
     *           complete {
     *             created {
     *               (CreateResource ? actor).mapTo[Try[String]]
     *             }
     *           }
     *         }
     *       }
     *   }
     * }}}
     */
    def joinCode[T](code: StatusCode, resp: T)(implicit m: Marshaller[T]): ToResponseMarshallable = {
      implicit val joinM: ToResponseMarshaller[(StatusCode, T)] = fromStatusCodeAndT((s: StatusCode) => s, m)
      isMarshallable(code -> resp)
    }
  }

  trait StatusCodeJoiners {
    import StatusCodeJoiners._

    /**
     * Bundles a service response with the HTTP code 200 (OK)
     *
     * Note that this function may be used for clarity, but is not required, since 200 is the default code used by Spray
     */
    def ok[T](r: T)(implicit m: Marshaller[T]): ToResponseMarshallable = joinCode(OK, r)

    /**
     * Bundles a service response with the HTTP code 201 (CREATED)
     */
    def created[T](r: T)(implicit m: Marshaller[T]): ToResponseMarshallable = joinCode(Created, r)

    /**
     * Bundles a service response with the HTTP code 202 (ACCEPTED)
     */
    def accepted[T](r: T)(implicit m: Marshaller[T]): ToResponseMarshallable = joinCode(Accepted, r)

    /**
     * Bundles a service response with the HTTP code 204 (NO CONTENT)
     */
    def noContent[T](r: T)(implicit m: Marshaller[T]): ToResponseMarshallable = joinCode(NoContent, r)
  }

  object PacBioServiceErrors {
    sealed abstract class PacBioServiceError(message: String, cause: Throwable = null)
      extends Exception(message, cause) {

      val code: StatusCode
      def response: ThrowableResponse = {
        val stackTrace = Option(cause).map { t: Throwable =>
          val sw = new StringWriter
          t.printStackTrace(new PrintWriter(sw))
          s"\n$sw"
        }.getOrElse("")
        ThrowableResponse(code.intValue, message + stackTrace, code.reason)
      }
    }

    class ResourceNotFoundError(message: String, cause: Throwable = null)
      extends PacBioServiceError(message, cause) { override val code = NotFound }
    class MethodNotImplementedError(message: String, cause: Throwable = null)
      extends PacBioServiceError(message, cause) { override val code = NotImplemented }
    class ConflictError(message: String, cause: Throwable = null)
      extends PacBioServiceError(message, cause) { override val code = Conflict }
    class UnprocessableEntityError(message: String, cause: Throwable = null)
      extends PacBioServiceError(message, cause) { override val code = UnprocessableEntity }
    class UnknownServerError(message: String, cause: Throwable = null)
      extends PacBioServiceError(message, cause) { override val code = InternalServerError }
  }

  trait PacBioServiceErrors extends BasicToResponseMarshallers with LazyLogging {
    import PacBioServiceErrors._
    import SprayJsonSupport._
    import PacBioJsonProtocol.pbThrowableResponseFormat
    import CORSSupport._

    def response(e: Throwable): ThrowableResponse = e match {
      case t: PacBioServiceError => t.response
      case NonFatal(t) => new UnknownServerError(t.getMessage, t).response
    }

    val responseMarshaller: ToResponseMarshaller[(Int, ThrowableResponse)] =
      fromStatusCodeAndT(
        (s: Int) => StatusCodes.getForKey(s).getOrElse(InternalServerError),
        sprayJsonMarshallerConverter(pbThrowableResponseFormat))

    def addAccessControlHeader(headers: List[HttpHeader]): List[HttpHeader] =
      if (headers.forall(!_.isInstanceOf[`Access-Control-Allow-Origin`]))
        allowOriginHeader :: headers
      else
        headers

    implicit val pacbioExceptionHandler: ExceptionHandler = ExceptionHandler {
      case NonFatal(e) => ctx =>
        val resp = response(e)
        val message = if (resp.message == null) {
          e.getStackTrace.mkString("\n  ")
        } else {
          resp.message
        }
        logger.warn(s"Non-fatal exception: ${resp.httpCode} - ${resp.errorType} - ${message} Request ${ctx.request}")
        ctx
          // Exception handling bypasses CORSSupport, so we add the header here
          .withHttpResponseHeadersMapped(addAccessControlHeader)
          .complete(resp.httpCode, resp)(responseMarshaller)
    }

    implicit val pacbioRejectionHandler: RejectionHandler = RejectionHandler.Default.andThen { route =>
      route.compose[RequestContext]{ rc =>
        rc.withHttpResponseHeadersMapped(addAccessControlHeader).withHttpResponseMapped { resp =>
          if (resp.status.isFailure) {
            logger.warn(s"Request rejected: ${resp.status.intValue} - ${resp.status.reason} - ${resp.entity.asString}")
            val tResp = ThrowableResponse(resp.status.intValue, resp.entity.asString, resp.status.reason)
            val bResp = pbThrowableResponseFormat.write(tResp).prettyPrint.getBytes
            resp.copy(entity = HttpEntity(ContentTypes.`application/json`, bResp))
          } else {
            resp
          }
        }
      }
    }
  }

  trait PacBioService extends StatusCodeJoiners with Directives with ServiceIdUtils {
    import CORSSupport._

    val manifest: PacBioComponentManifest
    def routes: Route

    def prefixedRoutes = cors { routes }
  }

  class RoutedHttpService(route: Route) extends Actor with HttpService with ActorLogging with PacBioServiceErrors {
    import PacBioJsonProtocol.pbThrowableResponseFormat
    import CORSSupport._

    implicit def actorRefFactory = context

    def showRequest(req: HttpRequest): LogEntry = {
      var asString: String = req.entity.toOption.map(
          data => s"request: ${req.method} ${req.uri} ${data}"
        ).getOrElse(s"request: ${req.method} ${req.uri}")
      LogEntry(asString, akka.event.Logging.InfoLevel)
    }

    def receive: Receive = runRoute(compressResponseIfRequested() {
      logRequest(showRequest _) {
        route
      }
    })(
      pacbioExceptionHandler,
      pacbioRejectionHandler,
      context,
      RoutingSettings.default,
      LoggingContext.fromActorRefFactory)

    override def timeoutRoute = complete {
      val tResp = ThrowableResponse(InternalServerError.intValue,"The server was not able to produce a timely response to your request.", InternalServerError.reason)
      val bResp = pbThrowableResponseFormat.write(tResp).prettyPrint.getBytes

      HttpResponse(
        InternalServerError,
        HttpEntity(ContentTypes.`application/json`, bResp),
        List(allowOriginHeader)
      )
    }
  }

  trait ServiceIdUtils {

    private def toServiceId(n: PacBioNamespaces.PacBioNamespace, s: String) = s"pacbio.${n.name}.${s.toLowerCase()}"

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
    def toToolId(s: String): String = toServiceId(PacBioNamespaces.SMRTTools, s)

    def toAppId(s: String): String = toServiceId(PacBioNamespaces.SMRTApps, s)

    def toServiceId(s: String): String = toServiceId(PacBioNamespaces.SMRTServices, s)
  }
}
