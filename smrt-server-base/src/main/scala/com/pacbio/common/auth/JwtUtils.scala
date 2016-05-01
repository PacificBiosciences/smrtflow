package com.pacbio.common.auth

import java.security.Signature

import org.json4s._
import org.json4s.Extraction

import authentikat.jwt.{JsonWebToken, JwtClaimsSet, JwtHeader}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.time.{PacBioDateTimeFormat, ClockProvider, Clock}
import org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString
import org.joda.time.{DateTime => JodaDateTime, Duration => JodaDuration}

import scala.concurrent.duration._
import scala.language.implicitConversions

// TODO(smcclellan): Add unit tests

/**
 * Trait for classes that can create and validate JWTs.
 */
trait JwtUtils {
  /**
   * Constructs a JWT from the given user info.
   */
  def getJwt(user: ApiUser): String

  /**
   * Validates a JWT, returning the user login if the JWT is valid.
   */
  def validate(jwt: String): Option[String]
}

/**
 * Abstract provider that provides a singleton JwtUtils.
 */
trait JwtUtilsProvider {
  val jwtUtils: Singleton[JwtUtils]
}

case class ClaimsSet(id: String,
  userName: String,
  email: String,
  firstName: String,
  lastName: String,
  iat: JodaDateTime,
  exp: JodaDateTime,
  roles: Set[String],
  iss: String,
  sub: String)

/**
 * Concrete implementation of JwtUtils.
 */
class JwtUtilsImpl(clock: Clock) extends JwtUtils {
  import PacBioDateTimeFormat.DATE_TIME_FORMAT

  // TODO(smcclellan): Make these values configurable?
  val ISSUER = "pacificbiosciences.com"
  val TTL = new JodaDuration(1.hour.toMillis)


  private object DateTimeSerializer extends CustomSerializer[JodaDateTime](format => (
  {
    case JString(s) => JodaDateTime.parse(s, DATE_TIME_FORMAT)
    case JNull => null
  },
  {
    case d: JodaDateTime => JString(d.toString(DATE_TIME_FORMAT))
  }
  ))

  private object RoleSerializer extends CustomSerializer[Role](format => (
  {
    case JString(s) => Role.fromString(s) match {
      case Some(r) => r
      case None => null
    }
    case JNull => null
  },
  {
    case r: Role => JString(r.toString)
  }
  ))

  private implicit val claimFormats =
    DefaultFormats + DateTimeSerializer + RoleSerializer

  private def toClaimsSet(jClaims: JValue): Option[ClaimsSet] = {
    jClaims.extractOpt[ClaimsSet]
  }

  private def toJwtClaimsSet(claims: ClaimsSet): JwtClaimsSet = {
    JwtClaimsSet(Extraction.decompose(claims))
  }

  def getJwt(user: ApiUser): String = {
    val now = clock.dateNow()

    val header = JwtHeader("RS256")

    val claimsSet = ClaimsSet(
      id = user.id,
      userName = user.login,
      email = user.email.getOrElse(""),
      firstName = user.firstName.getOrElse(""),
      lastName = user.lastName.getOrElse(""),
      iat = now,
      exp = now.plus(TTL),
      roles = user.roles.map(_.toString),
      iss = ISSUER,
      sub = "")
    getRsaJwt(header, toJwtClaimsSet(claimsSet))
  }

  def validate(jwt: String): Option[String] = {
    validateRsa(jwt)
  }

  // JWT for scala does not yet implement RS256, so we have to roll our own signing method. Once implemented, we should
  // be able to replace this with authentikat.jwt.JsonWebToken(header, claimsSet, PRIVATE_KEY).
  //
  // See https://github.com/jasongoodwin/authentikat-jwt/blob/master/src/main/scala/authentikat/jwt/
  private def getRsaJwt(header: JwtHeader, claims: JwtClaimsSet): String = {
    val encodedHeader = encodeBase64URLSafeString(header.asJsonString.getBytes("UTF-8"))
    val encodedClaims = encodeBase64URLSafeString(claims.asJsonString.getBytes("UTF-8"))

    val signingInput = encodedHeader + "." + encodedClaims
    val encodedSignature: String = getRsaSignature(signingInput)

    signingInput + "." + encodedSignature
  }

  // JWT for scala does not yet implement RS256, so we have to roll our own validation method. Once implemented, we
  // should be able to replace this with authentikat.jwt.JsonWebToken.validate(jwt, PRIVATE_KEY).
  //
  // See https://github.com/jasongoodwin/authentikat-jwt/blob/master/src/main/scala/authentikat/jwt/
  private def validateRsa(jwt: String): Option[String] = {

    jwt.split("\\.") match {
      case Array(providedHeader, providedClaims, providedSignature) =>
        val expectedSignature = getRsaSignature(providedHeader + "." + providedClaims)
        if (!providedSignature.contentEquals(expectedSignature)) return None

        for {
          (_, claims, _) <- JsonWebToken.unapply(jwt)
          claimsSet <- toClaimsSet(claims.jvalue)
          if claimsSet.exp isAfter clock.dateNow()
        } yield claimsSet.userName
      case _ =>
        None
    }
  }

  private def getRsaSignature(signingInput: String): String = {
    import Keys.PRIVATE_KEY

    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(PRIVATE_KEY)
    signature.update(signingInput.getBytes)
    encodeBase64URLSafeString(signature.sign)
  }
}

/**
 * Provides a singleton JwtUtilsImpl. Concrete providers must mixin a ClockProvider.
 */
trait JwtUtilsImplProvider extends JwtUtilsProvider {
  this: ClockProvider =>

  override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtilsImpl(clock()))
}
