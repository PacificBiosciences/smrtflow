package com.pacbio.common.auth

import com.pacbio.common.actors.{UserDao, UserDaoProvider}
import com.pacbio.common.dependency.{Singleton, TypesafeSingletonReader}
import spray.http._
import spray.routing.authentication.{BasicAuth, UserPass}
import spray.routing.directives.AuthMagnet
import spray.routing.{AuthenticationFailedRejection, Rejection, RequestContext}

import scala.concurrent.{ExecutionContext, Future}

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
  def isUserOrRoot(l: String): Boolean = login.compareToIgnoreCase(l) == 0 || hasPermission(ROOT)

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
  // TODO(smcclellan): Rename these "basicAuth", "jwtAuth", and "wso2Auth" or similar
  // TODO(smcclellan): Remove dead code when wso2 auth is implemented everywhere

  /**
   * Performs authentication using the Basic scheme, with a login and password, e.g. "Authorization: Basic user:pass"
   */
  def userPassAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo]

  /**
   * Performs authentication using a JWT in a custom scheme similar to OAuth's Bearer scheme., e.g.
   * "Authorization: Bearer jwtstring"
   */
  def smrtLinkJwtAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo]

  /**
   * Parses claims passed to SMRTLink from WSO2 as a JWT. Does not validate the JWT signature.
   */
  def jwtAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo]
}

abstract class AbstractAuthenticator(jwtUtils: JwtUtils) extends Authenticator {
  override def jwtAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    import AuthenticationFailedRejection._

    def authHeader(ctx: RequestContext) = ctx.request.headers.find(_.is("x-jwt-assertion"))

    def toUser(login: String): ApiUser = {
      val normalizedLogin = if (login.contains("@")) login.split("@")(0) else login
      ApiUser(login = normalizedLogin, id = normalizedLogin, email = None, firstName = None, lastName = None)
    }

    def validateToken(ctx: RequestContext): Future[Option[AuthInfo]] = Future {
      // Expect JWT to be passed as "X-JWT-Assertion: jwtstring"
      authHeader(ctx)
        .map(_.value)                                           // Render header as string
        .flatMap(jwt => jwtUtils.parse(jwt, WSO2ClaimsDialect)) // Parse JWT and get user login
        .map(toUser)                                            // Get user from DAO
        .map(user => new AuthInfo(user))                        // Convert user object to AuthInfo object
    }

    def authenticate(ctx: RequestContext): Future[Either[Rejection, AuthInfo]] =
      validateToken(ctx).map {
        case Some(authInfo) => Right(authInfo)
        case None =>
          val cause = if (authHeader(ctx).isEmpty) CredentialsMissing else CredentialsRejected
          Left(AuthenticationFailedRejection(cause, List.empty))
      }

    ctx: RequestContext => authenticate(ctx)
  }
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
class AuthenticatorImpl(userDao: UserDao, jwtUtils: JwtUtils) extends AbstractAuthenticator(jwtUtils) {
  val REALM = "pacificbiosciences.com"

  override def userPassAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    def validateUser(userPass: Option[UserPass]): Future[Option[AuthInfo]] = {
      userPass.map { up =>
        userDao
          .authenticate(up.user, up.pass)
          .map(new AuthInfo(_))
          .map(Some(_))
          .recover { case _ => None }
      }.getOrElse(Future.successful(None))
    }

    BasicAuth(validateUser _, realm = REALM)
  }



  override def smrtLinkJwtAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    import AuthenticationFailedRejection._
    import HttpHeaders._
    import spray.util._

    def authHeader(ctx: RequestContext) = ctx.request.headers.findByType[`Authorization`]

    def validateToken(ctx: RequestContext): Future[Option[AuthInfo]] = {
      // Expect JWT to be passed in authorization header as "Authorization: Bearer jwtstring"
      authHeader(ctx)
        .map(_.renderValue(new StringRendering).get)    // Render header as string, e.g. "Bearer jwtstring"
        .map(_.split(" "))                              // Split into words, e.g. Array("Bearer", "jwtstring")
        .withFilter(_.length == 2)                      // Filter out headers w/ wrong number of words
        .withFilter(_.head == "Bearer")                 // Filter out headers w/ wrong first word
        .map(_(1))                                      // Get second word, which should be the JWT
        .flatMap(jwt => jwtUtils.validate(jwt))         // Validate JWT, and get user login
        .map(login => userDao.getUser(login))           // Get user from DAO
        .map(_.map(user => new AuthInfo(user)))         // Convert user object to AuthInfo object
        .map(_.map(Some(_)).recover { case _ => None }) // Convert AuthInfo to Option (None if getUser fails)
        .getOrElse(Future.successful(None))             // Option[Future[Option[_]]] -> Future[Option[_]]
    }

    val challengeHeaders =
      `WWW-Authenticate`(HttpChallenge(scheme = "Bearer", realm = REALM, params = Map.empty)) :: Nil

    def authenticate(ctx: RequestContext): Future[Either[Rejection, AuthInfo]] =
      validateToken(ctx).map {
        case Some(authInfo) => Right(authInfo)
        case None =>
          val cause = if (authHeader(ctx).isEmpty) CredentialsMissing else CredentialsRejected
          Left(AuthenticationFailedRejection(cause, challengeHeaders))
      }

    ctx: RequestContext => authenticate(ctx)
  }
}

/**
 * Implementation of Authenticator that ignores credentials and returns a fake AuthInfo with ROOT privileges.
 */
class FakeAuthenticator(jwtUtils: JwtUtils) extends AbstractAuthenticator(jwtUtils) {
  import UserDao.ROOT_USER

  override def userPassAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
    ctx: RequestContext => Future { Right(new AuthInfo(ROOT_USER)) }
  }

  override def smrtLinkJwtAuth(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
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
    case false => new FakeAuthenticator(jwtUtils())
  })
}

/**
 * Provides a singleton FakeAuthenticator.
 */
trait FakeAuthenticatorProvider extends AuthenticatorProvider {
  this: JwtUtilsProvider =>

  override val authenticator: Singleton[Authenticator] = Singleton(() => new FakeAuthenticator(jwtUtils()))
}