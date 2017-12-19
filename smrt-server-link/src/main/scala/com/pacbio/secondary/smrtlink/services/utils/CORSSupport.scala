package com.pacbio.secondary.smrtlink.services.utils

import akka.http.scaladsl.server._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{
  HttpMethod,
  HttpMethods,
  HttpRequest,
  HttpResponse
}
import akka.http.scaladsl.server.RouteResult.Rejected
import akka.http.scaladsl.server.directives.BasicDirectives

/**
  * Forked from
  *
  * https://gist.github.com/joseraya/176821d856b43b1cfe19
  *
  * See Also
  * https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS
  * http://www.html5rocks.com/en/tutorials/cors/#toc-adding-cors-support-to-the-server
  *
  * MK(12-7-2017) We should just use this https://github.com/lomigmegard/akka-http-cors
  * Someone else has solved this problem.
  */
object CORSSupport extends BasicDirectives {

  val allOrigins: HttpOriginRange = HttpOriginRange.*
  val allowOriginHeader = `Access-Control-Allow-Origin`(allOrigins)
  val optionsCorsHeaders = List(
    `Access-Control-Allow-Headers`(
      "Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent, Authorization"),
    `Access-Control-Max-Age`(1728000)
  )

//  val cors: Directive0 = mapRequestContext { ctx =>
//    ctx
//      .withRouteResponseHandling {
//        //It is an option request for a resource that responds to some other method
//        case Rejected(rejections)
//            if ctx.request.method.equals(HttpMethods.OPTIONS) && rejections
//              .exists(_.isInstanceOf[MethodRejection]) =>
//          val allowedMethods: List[HttpMethod] =
//            rejections
//              .filter(_.isInstanceOf[MethodRejection])
//              .map(_.asInstanceOf[MethodRejection].supported)
//
//          ctx.complete(
//            val customHeaders: Seq[HttpMethod] = Seq(HttpMethods.OPTIONS, allowedMethods: _*) :: allowOriginHeader ::
//        optionsCorsHeaders
//            HttpResponse().withHeaders(
//              `Access-Control-Allow-Methods`(HttpMethods.OPTIONS, allowedMethods: _*) :: allowOriginHeader ::
//                optionsCorsHeaders
//            ))
//      }
//      .withHttpResponseHeadersMapped { headers =>
//        allowOriginHeader :: headers
//      }
//  }
}
