package com.pacbio.secondary.smrtlink.auth

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.UserRecord
import spray.routing.directives.AuthMagnet
import spray.routing.{AuthenticationFailedRejection, Rejection, RequestContext}

import scala.concurrent.{ExecutionContext, Future}

// TODO(smcclellan): Add unit tests

/**
  * Contains methods that can be passed as a parameter to the spray authenticate directive in order to extract a
  * UserRecord. E.g.:
  *
  * {{{
  *   pathPrefix("api") {
  *     path("jwtProtected") {
  *       authenticate(myAuthenticator.wso2Auth) { user =>
  *         get {
  *           // All authenticated users can enter here
  *           complete("Hi, " + user.getDisplayName)
  *         }
  *       }
  *     }
  *   }
  * }}}
  */
trait Authenticator {

  /**
    * Parses claims passed to SMRTLink from WSO2 as a JWT. Does not validate the JWT signature.
    */
  def wso2Auth(implicit ec: ExecutionContext): AuthMagnet[UserRecord]
}

object Authenticator {
  val JWT_HEADER = "x-jwt-assertion"
}

/**
  * Provides a singleton Authenticator. Concrete providers must define the authenticator val.
  */
trait AuthenticatorProvider {
  val authenticator: Singleton[Authenticator]
}

/**
  * Implementation of Authenticator that checks user/pass credentials against a UserDao, and verifies JWT credentials
  * with a JwtUtils.
  */
class AuthenticatorImpl(jwtUtils: JwtUtils) extends Authenticator {
  import Authenticator.JWT_HEADER

  override def wso2Auth(
      implicit ec: ExecutionContext): AuthMagnet[UserRecord] = {
    import AuthenticationFailedRejection._

    def authHeader(ctx: RequestContext) =
      ctx.request.headers.find(_.is(JWT_HEADER))

    def validateToken(ctx: RequestContext): Future[Option[UserRecord]] =
      Future {
        // Expect JWT to be passed as "X-JWT-Assertion: jwtstring"
        authHeader(ctx)
          .map(_.value) // Render header as string
          .flatMap(jwt => jwtUtils.parse(jwt)) // Parse JWT and get claims
      }

    def authenticate(
        ctx: RequestContext): Future[Either[Rejection, UserRecord]] =
      validateToken(ctx).map {
        case Some(user) => Right(user)
        case None =>
          val cause =
            if (authHeader(ctx).isEmpty) CredentialsMissing
            else CredentialsRejected
          Left(
            AuthenticationFailedRejection(cause,
                                          challengeHeaders = List.empty))
      }

    ctx: RequestContext =>
      authenticate(ctx)
  }
}

/**
  * Provides a singleton AuthenticatorImpl. Concrete providers must mixin a JwtUtilsProvider.
  */
trait AuthenticatorImplProvider extends AuthenticatorProvider {
  this: JwtUtilsProvider =>

  override val authenticator: Singleton[Authenticator] = Singleton(
    () => new AuthenticatorImpl(jwtUtils()))
}
