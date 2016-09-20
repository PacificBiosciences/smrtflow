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

  // TODO(smcclellan): This is for testing. Get real roles from WSO2 claims?
  val ROLES_CLAIM = "http://nanofluidics.com/claims/roles"
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
      jobject        <- Try(claims.jvalue.asInstanceOf[JObject]).toOption  .map(x => {println("JWT CLAIMS:"); x.values.foreach(v => println(s"${v._1} -> ${v._2}")); x})
      unclaim        <- jobject.values.get(USERNAME_CLAIM)
      rclaim         <- jobject.values.get(ROLES_CLAIM)
      username       <- Try(unclaim.asInstanceOf[String]).toOption
      roles          <- Try(rclaim.asInstanceOf[String]).toOption
                          .map(r => Set(r.split(","):_*))
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
