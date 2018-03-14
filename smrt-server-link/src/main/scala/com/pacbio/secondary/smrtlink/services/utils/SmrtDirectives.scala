package com.pacbio.secondary.smrtlink.services.utils

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.{
  CredentialsMissing,
  CredentialsRejected
}
import akka.http.scaladsl.server.{
  AuthenticationFailedRejection,
  Directive,
  ValidationRejection
}
import com.pacbio.secondary.smrtlink.auth.JwtUtilsImpl
import com.pacbio.secondary.smrtlink.models.UserRecord

object SmrtDirectives {

  private val JWT_HEADER = "x-jwt-assertion"
  // This should really be simplified, We don't need all these unnecessary abstractions
  private val jwtUtils = new JwtUtilsImpl

  private def parseFromHeaders(headers: Seq[HttpHeader]): Option[UserRecord] =
    headers
      .find(_.is(JWT_HEADER))
      .map(_.value())
      .flatMap(jwtUtils.parse)

  def extractOptionalUserRecord: Directive[Tuple1[Option[UserRecord]]] =
    Directive[Tuple1[Option[UserRecord]]] { inner => ctx =>
      inner(Tuple1(parseFromHeaders(ctx.request.headers)))(ctx)
    }

  def extractRequiredUserRecord: Directive[Tuple1[UserRecord]] =
    Directive[Tuple1[UserRecord]] { inner => ctx =>
      parseFromHeaders(ctx.request.headers) match {
        case Some(userRecord) => inner(Tuple1(userRecord))(ctx)
        case _ =>
          val cause = ctx.request.headers.find(_.is(JWT_HEADER)) match {
            case Some(_) =>
              // This should distinguish between rejected and invalid format
              CredentialsRejected
            case _ => CredentialsMissing
          }
          ctx.reject(
            AuthenticationFailedRejection(cause,
                                          HttpChallenge("http", realm = None)))
      }
    }

}
