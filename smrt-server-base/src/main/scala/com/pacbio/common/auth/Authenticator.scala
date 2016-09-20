package com.pacbio.common.auth

import com.pacbio.common.dependency.{Singleton, TypesafeSingletonReader}
import com.pacbio.common.models.{Roles, UserRecord}
import spray.routing.directives.AuthMagnet
import spray.routing.{AuthenticationFailedRejection, Rejection, RequestContext}

import scala.concurrent.{ExecutionContext, Future}

// TODO(smcclellan): Add unit tests

/**
 * Represents all the information necessary to perform authorization.
 */
sealed trait AuthInfo {
  /**
   * The login for the authenticated user.
   */
  val login: String

  /**
   * The PacBio roles of the authenticated user
   */
  val roles: Set[Roles.Role]

  /**
   * Determines whether the authenticated user is the given user. (Or has Admin privileges.)
   */
  def isUserOrAdmin(l: String): Boolean = login.compareToIgnoreCase(l) == 0 || hasPermission(Roles.PbAdmin)

  /**
   * Determines whether the authenticated user can act as one of the given roles.
   */
  def hasPermission(r: Roles.Role*): Boolean = roles.intersect(r.toSet).nonEmpty
}

/**
 * An AuthInfo based on a claims set extracted from a JWT
 */
final class JwtAuthInfo(user: UserRecord) extends AuthInfo {
  override val login: String = user.userName
  override val roles: Set[Roles.Role] = user.roles
}

/**
 * An AuthInfo that represents a generic root user with all permissions
 */
object RootAuthInfo extends AuthInfo {
  override val login: String = "root"
  override val roles: Set[Roles.Role] = Set(Roles.PbAdmin, Roles.PbLabTech, Roles.PbBioinformatician)
}

/**
 * Contains methods that can be passed as a parameter to the spray authenticate directive in order to extract an
 * AuthInfo. E.g.:
 *
 * {{{
 *   pathPrefix("api") {
 *     path("jwtProtected") {
 *       authenticate(myAuthenticator.wso2Auth) { authInfo =>
 *         get {
 *           // All authenticated users can enter here
 *           complete("Hi, " + authInfo.login)
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
  def wso2Auth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo]
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

  override def wso2Auth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    import AuthenticationFailedRejection._

    def authHeader(ctx: RequestContext) = ctx.request.headers.find(_.is(JWT_HEADER))

    def validateToken(ctx: RequestContext): Future[Option[AuthInfo]] = Future {
      // Expect JWT to be passed as "X-JWT-Assertion: jwtstring"
      authHeader(ctx)
        .map(_.value)                           // Render header as string
        .flatMap(jwt => jwtUtils.parse(jwt))    // Parse JWT and get claims
        .map(claims => new JwtAuthInfo(claims)) // Convert user object to AuthInfo object
    }

    def authenticate(ctx: RequestContext): Future[Either[Rejection, AuthInfo]] =
      validateToken(ctx).map {
        case Some(authInfo) => Right(authInfo)
        case None =>
          val cause = if (authHeader(ctx).isEmpty) CredentialsMissing else CredentialsRejected
          Left(AuthenticationFailedRejection(cause, challengeHeaders = List.empty))
      }

    ctx: RequestContext => authenticate(ctx)
  }
}

/**
 * Implementation of Authenticator that ignores credentials and returns a generic root AuthInfo.
 */
class FakeAuthenticator extends Authenticator {
  override def wso2Auth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    ctx: RequestContext => Future { Right(RootAuthInfo) }
  }
}

trait EnableAuthenticationConfig {
  final val enableAuthentication: Singleton[Boolean] =
    TypesafeSingletonReader.fromConfig().getBoolean("enable-auth").orElse(false)
}

/**
 * Provides a singleton AuthenticatorImpl, if enable-ldap-auth config is set to true.  Otherwise, it provides a
 * FakeAuthenticator.  Concrete providers must mixin a UserDaoProvider and a JwtUtilsProvider.
 */
trait AuthenticatorImplProvider extends AuthenticatorProvider with EnableAuthenticationConfig {
  this: JwtUtilsProvider =>

  override val authenticator: Singleton[Authenticator] = Singleton(() => enableAuthentication() match {
    case true => new AuthenticatorImpl(jwtUtils())
    case false => new FakeAuthenticator
  })
}

/**
 * Provides a singleton FakeAuthenticator.
 */
trait FakeAuthenticatorProvider extends AuthenticatorProvider {
  override val authenticator: Singleton[Authenticator] = Singleton(() => new FakeAuthenticator())
}