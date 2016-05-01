package com.pacbio.common.services.utils

import spray.http.HttpMethods._
import spray.http.{HttpResponse, HttpMethod, HttpMethods, AllOrigins}
import spray.http.HttpHeaders.{`Access-Control-Allow-Methods`, `Access-Control-Max-Age`, `Access-Control-Allow-Headers`, `Access-Control-Allow-Origin`}
import spray.routing._
import spray.routing.directives.BasicDirectives

/**
 * Forked from
 *
 * https://gist.github.com/joseraya/176821d856b43b1cfe19
 *
 * See Also
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS
 * http://www.html5rocks.com/en/tutorials/cors/#toc-adding-cors-support-to-the-server
 */
object CORSSupport extends BasicDirectives {

  val allowOriginHeader = `Access-Control-Allow-Origin`(AllOrigins)
  val optionsCorsHeaders = List(
    `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent, Authorization"),
    `Access-Control-Max-Age`(1728000))

  val cors: Directive0 = mapRequestContext { ctx => ctx.withRouteResponseHandling {
    //It is an option request for a resource that responds to some other method
    case Rejected(rejections) if ctx.request.method.equals(HttpMethods.OPTIONS) && rejections.exists(_.isInstanceOf[MethodRejection]) =>
      val allowedMethods: List[HttpMethod] =
        rejections.filter(_.isInstanceOf[MethodRejection]).map(_.asInstanceOf[MethodRejection].supported)

      ctx.complete(HttpResponse().withHeaders(
        `Access-Control-Allow-Methods`(OPTIONS, allowedMethods: _*) :: allowOriginHeader ::
          optionsCorsHeaders
      ))
    }.withHttpResponseHeadersMapped { headers => allowOriginHeader :: headers}
  }
}
