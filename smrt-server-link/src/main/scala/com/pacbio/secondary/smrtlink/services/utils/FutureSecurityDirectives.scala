package com.pacbio.secondary.smrtlink.services.utils

import scala.language.implicitConversions
import akka.http.scaladsl.server._
import spray.routing.directives.BasicDirectives._
import spray.routing.directives._
import scala.concurrent.{ExecutionContext, Future}
import FutureDirectives._

// from https://gist.github.com/larryboymi/2838db7c476873a71d22
trait FutureSecurityDirectives extends SecurityDirectives {
  def futureAuthorize(check: AuthorizeMagnet): Directive0 = check.directive
}

class AuthorizeMagnet(authDirective: Directive1[Boolean])(
    implicit executor: ExecutionContext)
    extends SecurityDirectives {
  val directive: Directive0 = authDirective.flatMap(authorize(_))
}

object AuthorizeMagnet {
  implicit def fromFutureAuth(auth: => Future[Boolean])(
      implicit executor: ExecutionContext): AuthorizeMagnet =
    new AuthorizeMagnet(onSuccess(auth))

  implicit def fromFutureAuthWithCtx(auth: RequestContext => Future[Boolean])(
      implicit executor: ExecutionContext): AuthorizeMagnet =
    new AuthorizeMagnet(extract(auth).flatMap(onSuccess(_)))
}
