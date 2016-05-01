package com.pacbio.common.auth

import com.pacbio.common.actors.{UserDaoProvider, UserDao}
import com.pacbio.common.dependency.{TypesafeSingletonReader, Singleton}
import spray.http._
import spray.routing.{AuthenticationFailedRejection, RequestContext}
import spray.routing.authentication.{BasicAuth, UserPass}
import spray.routing.directives.AuthMagnet

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try

// TODO(smcclellan): Add unit tests

/**
 * Represents all the information necessary to perform authorization.
 */
final class AuthInfo(user: ApiUser) {
  import BaseRoles.ROOT

  /**
   * The login for the authenticated user.
   */
  val login: String = user.login

  /**
   * Determines whether the authenticated user is the given user.
   */
  def isUserOrRoot(login: String): Boolean = user.login == login || hasPermission(ROOT)

  /**
   * Determines whether the authenticated user can act as the given role.
   */
  def hasPermission(role: Role): Boolean =
    if (user.roles.isEmpty) false
    else user.roles.exists(_ >= role)
}

/**
 * Contains methods that can be passed as a parameter to the spray authenticate directive in order to extract an
 * AuthInfo. E.g.:
 *
 * {{{
 *   pathPrefix("api") {
 *     path("userPassProtected") {
 *       authenticate(myAuthenticator.userPassAuth) { authInfo =>
 *         get {
 *           // All authenticated users can enter here
 *           complete("Hi, " + authInfo.user.login)
 *         }
 *       }
 *     } ~
 *     path("jwtProtected") {
 *       authenticate(myAuthenticator.jwtAuth) { authInfo =>
 *         get {
 *           // All authenticated users can enter here
 *           complete("Hi, " + authInfo.user.login)
 *         }
 *       }
 *     }
 *   }
 * }}}
 */
trait Authenticator {
  /**
   * Performs authentication using the Basic scheme, with a login and password, e.g. "Authorization: Basic user:pass"
   */
  def userPassAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo]

  /**
   * Performs authentication using a JWT in a custom scheme similar to OAuth's Bearer scheme., e.g.
   * "Authorization: Bearer jwtstring"
   */
  def jwtAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo]
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
class AuthenticatorImpl(userDao: UserDao, jwtUtils: JwtUtils) extends Authenticator {
  val REALM = "pacificbiosciences.com"

  override def userPassAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    def validateUser(userPass: Option[UserPass]): Option[AuthInfo] =
      for {
        up <- userPass
        user <- Try(userDao.authenticate(up.user, up.pass)).toOption
      } yield new AuthInfo(user)

    def authenticator(userPass: Option[UserPass]): Future[Option[AuthInfo]] = Future { validateUser(userPass) }

    BasicAuth(authenticator _, realm = REALM)
  }

  override def jwtAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    import spray.util._
    import HttpHeaders._
    import AuthenticationFailedRejection._

    def authHeader(ctx: RequestContext) = ctx.request.headers.findByType[`Authorization`]

    def validateToken(ctx: RequestContext): Option[AuthInfo] = {
      // Expect JWT to be passed in authorization header as "Authorization: Bearer jwtstring"
      authHeader(ctx)
        .map(_.renderValue(new StringRendering).get)            // Render header as string, e.g. "Bearer jwtstring"
        .map(_.split(" "))                                      // Split into words, e.g. Array("Bearer", "jwtstring")
        .withFilter(_.length == 2)                              // Filter out headers w/ wrong number of words
        .withFilter(_.head == "Bearer")                         // Filter out headers w/ wrong first word
        .map(_(1))                                              // Get second word, which should be the JWT
        .flatMap(jwt => jwtUtils.validate(jwt))                 // Validate JWT, and get user login
        .flatMap(login => Try(userDao.getUser(login)).toOption) // Get user from DAO
        .map(user => new AuthInfo(user))                        // Convert user object to AuthInfo object
    }

    val challengeHeaders =
      `WWW-Authenticate`(HttpChallenge(scheme = "Bearer", realm = REALM, params = Map.empty)) :: Nil

    def authenticate(ctx: RequestContext) =
      validateToken(ctx) match {
        case Some(authInfo) => Right(authInfo)
        case None =>
          val cause = if (authHeader(ctx).isEmpty) CredentialsMissing else CredentialsRejected
          Left(AuthenticationFailedRejection(cause, challengeHeaders))
      }

    ctx: RequestContext => Future { authenticate(ctx) }
  }
}

/**
 * Implementation of Authenticator that ignores credentials and returns a fake AuthInfo with ROOT privileges.
 */
class FakeAuthenticator extends Authenticator {
  import UserDao.ROOT_USER

  override def userPassAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    ctx: RequestContext => Future { Right(new AuthInfo(ROOT_USER)) }
  }

  override def jwtAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    ctx: RequestContext => Future { Right(new AuthInfo(ROOT_USER)) }
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
  this: UserDaoProvider with JwtUtilsProvider =>

  override val authenticator: Singleton[Authenticator] = Singleton(() => enableAuthentication() match {
    case true => new AuthenticatorImpl(userDao(), jwtUtils())
    case false => new FakeAuthenticator
  })
}

/**
 * Provides a singleton FakeAuthenticator.
 */
trait FakeAuthenticatorProvider extends AuthenticatorProvider {
  override val authenticator: Singleton[Authenticator] = Singleton(() => new FakeAuthenticator)
}