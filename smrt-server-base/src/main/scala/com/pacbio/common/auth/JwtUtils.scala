package com.pacbio.common.auth

import com.pacbio.common.models.{Roles, UserRecord}
import org.json4s._

import authentikat.jwt.JsonWebToken
import com.pacbio.common.dependency.Singleton

import scala.language.implicitConversions
import scala.util.Try

// TODO(smcclellan): Add unit tests

object JwtUtils {
  val USERNAME_CLAIM = "http://wso2.org/claims/enduser"
  val ROLES_CLAIM = "http://wso2.org/claims/role"
}

/**
 * Trait for classes that can create and validate JWTs.
 */
trait JwtUtils {
  /**
   * Parses a JWT, returning the user login if possible. DOES NOT VALIDATE THE SIGNATURE.
   */
  def parse(jwt: String): Option[UserRecord]
}

/**
 * Abstract provider that provides a singleton JwtUtils.
 */
trait JwtUtilsProvider {
  val jwtUtils: Singleton[JwtUtils]
}

/**
 * Concrete implementation of JwtUtils.
 */
class JwtUtilsImpl extends JwtUtils {

  import JwtUtils._

  private implicit val claimFormats = DefaultFormats

  override def parse(jwt: String): Option[UserRecord] = {
    for {
      (_, claims, _) <- JsonWebToken.unapply(jwt)
      jobject        <- Try(claims.jvalue.asInstanceOf[JObject]).toOption
      unclaim        <- jobject.values.get(USERNAME_CLAIM)
      rclaim         <- jobject.values.get(ROLES_CLAIM)
      username       <- Try(unclaim.asInstanceOf[String]).toOption
                          .map(_.split("@").head)
      roles          <- Try(rclaim.asInstanceOf[List[String]]).toOption
                          .map(_.toSet)
                          .map(_.map(Roles.fromString))
                          .map(_.filter(_.isDefined))
                          .map(_.map(_.get))
    } yield UserRecord(username, roles)
  }
}

/**
 * Provides a singleton JwtUtilsImpl. Concrete providers must mixin a ClockProvider.
 */
trait JwtUtilsImplProvider extends JwtUtilsProvider {
  override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtilsImpl())
}
