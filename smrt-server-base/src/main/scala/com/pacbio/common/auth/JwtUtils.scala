package com.pacbio.common.auth

import com.pacbio.common.models.UserRecord
import org.json4s._

import authentikat.jwt.JsonWebToken
import com.pacbio.common.dependency.Singleton

import scala.language.implicitConversions
import scala.util.Try

// TODO(smcclellan): Add unit tests

object JwtUtils {
  // Required
  val USERNAME_CLAIM = "http://wso2.org/claims/enduser"
  val ROLES_CLAIM = "http://wso2.org/claims/role"
  val USER_EMAIL_CLAIM = "http://wso2.org/claims/emailaddress"

  // Optional
  val FIRST_NAME_CLAIM = "http://wso2.org/claims/givenname"
  val LAST_NAME_CLAIM = "http://wso2.org/claims/lastname"
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

  private def extractUsername(raw: String): String = raw.split("/").last.split("@").head

  override def parse(jwt: String): Option[UserRecord] = {
    for {
      (_, claims, _) <- JsonWebToken.unapply(jwt)
      cm <- Try(claims.jvalue.asInstanceOf[JObject].values).toOption
      ur <- Try {
        UserRecord(
          extractUsername(cm(USERNAME_CLAIM).asInstanceOf[String]),
          cm.get(USER_EMAIL_CLAIM).map(_.asInstanceOf[String]),
          cm.get(FIRST_NAME_CLAIM).map(_.asInstanceOf[String]),
          cm.get(LAST_NAME_CLAIM).map(_.asInstanceOf[String]),
          cm(ROLES_CLAIM).asInstanceOf[List[String]].toSet)
      }.toOption
    } yield ur
  }
}

/**
 * Provides a singleton JwtUtilsImpl. Concrete providers must mixin a ClockProvider.
 */
trait JwtUtilsImplProvider extends JwtUtilsProvider {
  override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtilsImpl())
}
