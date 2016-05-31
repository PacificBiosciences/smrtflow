package com.pacbio.common.database

import java.util.UUID

import com.pacbio.common.auth.ApiUser
import com.pacbio.common.dependency.Singleton
import com.unboundid.ldap.sdk.{ReadOnlyEntry, Attribute}

// TODO(smcclellan): Add unit tests

/**
 * Enum-like object set that represents different id types.
 */
object IdType {
  sealed abstract class IdType { def getIdAsString(attribute: Attribute): String }

  /**
   * A general-purpose id type that is stored as a string.
   */
  object STRING_ID extends IdType { override def getIdAsString(a: Attribute) = a.getValue }

  /**
   * A general-purpose id type that is stored as a long.
   */
  object LONG_ID extends IdType { override def getIdAsString(a: Attribute) = a.getValueAsLong.toString }

  /**
   * A UUID that is stored as a string.
   */
  object STRING_UUID extends IdType { override def getIdAsString(a: Attribute) = UUID.fromString(a.getValue).toString }

  /**
   * A GUID that is stored as an array of 16 bytes.
   */
  object BYTES_GUID extends IdType {
    override def getIdAsString(a: Attribute) = {
      val guid: Array[Byte] = a.getValueByteArray

      // If the hex string of the byte would be only one char, append a "0"
      def prefixZero(b: Integer): String = (if (b <= 0xF) "0" else "") + Integer.toHexString(b)

      // Get the byte at index i in the GUID byte array, and convert to two-char hex string
      def prefixedHexString(i: Integer): String = prefixZero(guid(i) & 0xFF)

      // Get the bytes at the given indices, convert to hex strings, and join
      def str(i: Integer*): String = i.map(prefixedHexString).mkString

      // The standard string representation of a GUID looks like "3f2504e0-4f89-41d3-9a0c-0305e82c3301" and takes the
      // bytes in this strange order: [3][2][1][0]-[5][4]-[7][6]-[8][9]-[10][11][12][13][14][15]. Unfortunately, there
      // doesn't seem to be a standard scala or java library for this conversion.
      Seq(str(3, 2, 1, 0), str(5, 4), str(7, 6), str(8, 9), str(10, 11, 12, 13, 14, 15)).mkString("-")
    }
  }

  val idTypeByName = Map(
    "STRING_ID" -> STRING_ID,
    "LONG_ID" -> LONG_ID,
    "STRING_UUID" -> STRING_UUID,
    "BYTES_GUID" -> BYTES_GUID
  )
}

/**
 * Configuration information for the user records stored in LDAP.
 */
case class LdapUsersConfig(
    usersDN: String,
    cacheSize: Int,
    usernameAttr: String,
    idAttr: String,
    idType: IdType.IdType,
    emailAttr: Option[String],
    firstNameAttr: Option[String],
    lastNameAttr: Option[String]) {
  val CN_ATTR = "cn"

  /**
   * Provides a Seq containing all the attributes that should be loaded into a user entry.
   */
  def attributes: Seq[String] = {
    var attrs = Seq(CN_ATTR, usernameAttr, idAttr)
    if (emailAttr.isDefined) attrs = attrs :+ emailAttr.get
    if (firstNameAttr.isDefined) attrs = attrs :+ firstNameAttr.get
    if (lastNameAttr.isDefined) attrs = attrs :+ lastNameAttr.get
    attrs
  }

  /**
   * Converts an LDAP entry with the correct attributes loaded into an ApiUser.
   */
  def getApiUser(entry: ReadOnlyEntry): ApiUser = {
    ApiUser(
      entry.getAttribute(usernameAttr).getValue,
      idType.getIdAsString(entry.getAttribute(idAttr)),
      emailAttr.flatMap(a => if (entry.hasAttribute(a)) Some(entry.getAttribute(a).getValue) else None),
      firstNameAttr.flatMap(a => if (entry.hasAttribute(a)) Some(entry.getAttribute(a).getValue) else None),
      lastNameAttr.flatMap(a => if (entry.hasAttribute(a)) Some(entry.getAttribute(a).getValue) else None))
  }
}

/**
 * General LDAP configuration.
 */
case class LdapConfig(host: String, port: Int)

/**
 * Provides all necessary configuration info to construct an LdapUserDao. Concrete providers should define the LDAP
 * users config and the LDAP config.
 */
trait LdapUserDaoConfigProvider {
  val ldapUsersConfig: Singleton[LdapUsersConfig]
  val ldapConfig: Singleton[LdapConfig]
}

/**
 * Companion object for TypesafeLdapUserDaoConfigProvider.
 */
object TypesafeLdapUserDaoConfigProvider {
  val DEFAULT_LDAP_PORT = 389
  val DEFAULT_CACHE_SIZE = 10

  val LDAP_CONFIG_PATH_PREFIX = "ldap"
  val LDAP_HOST_ATTR = "host"
  val LDAP_PORT_ATTR = "port" // Optional, default = 389

  val USERS_CONFIG_PATH_PREFIX = "users"
  val USERS_DN_ATTR = "dn"
  val USERS_CACHE_SIZE_ATTR = "cache-size" // Optional, default = 10
  val USER_USERNAME_ATTR = "username-attr"
  val USER_ID_ATTR = "id-attr"
  val USER_ID_TYPE_ATTR = "id-type"
  val USER_EMAIL_ATTR = "email-attr" // Optional
  val USER_FIRST_NAME_ATTR = "first-name-attr" // Optional
  val USER_LAST_NAME_ATTR = "last-name-attr" // Optional
}

/**
 * LdapUserDaoConfigProvider that reads the configuration from the ldap and ldap.users configs in application.conf. For
 * example:
 *
 * {{{
 *   ldap {
 *     host = "nanofluidics.com"
 *     port = 389
 *
 *     users {
 *       dn = "CN=Users,DC=Nanofluidics,DC=com"
 *       cache-size = 10
 *       username-attr = "sAMAccountName"
 *       id-attr = "objectGUID"
 *       id-type = "BYTES_GUID"
 *       email-attr = "mail"
 *       first-name-attr = "firstName"
 *       last-name-attr = "lastName"
 *     }
 *   }
 * }}}
 */
trait TypesafeLdapUserDaoConfigProvider extends LdapUserDaoConfigProvider {
  import TypesafeLdapUserDaoConfigProvider._
  import com.pacbio.common.dependency.MonadicSingletons._
  import com.pacbio.common.dependency.TypesafeSingletonReader._

  // Note that for-comprehensions using Singletons are experimental, and this pattern should not be reused elsewhere.

  override final val ldapUsersConfig: Singleton[LdapUsersConfig] = fromConfig()
    .in(LDAP_CONFIG_PATH_PREFIX)
    .getObject(USERS_CONFIG_PATH_PREFIX) { c =>
      for {
        dn <- c.getString(USERS_DN_ATTR).required
        cs <- c.getInt(USERS_CACHE_SIZE_ATTR).orElse(DEFAULT_CACHE_SIZE)
        un <- c.getString(USER_USERNAME_ATTR).required
        id <- c.getString(USER_ID_ATTR).required
        ty <- c.getString(USER_ID_TYPE_ATTR).required
        em <- c.getString(USER_EMAIL_ATTR).optional
        fn <- c.getString(USER_FIRST_NAME_ATTR).optional
        ln <- c.getString(USER_LAST_NAME_ATTR).optional
      } yield LdapUsersConfig(dn, cs, un, id, IdType.idTypeByName(ty), em, fn, ln)
    }.required

  override final val ldapConfig: Singleton[LdapConfig] = fromConfig()
    .getObject(LDAP_CONFIG_PATH_PREFIX) { c =>
      for {
        h <- c.getString(LDAP_HOST_ATTR).required
        p <- c.getInt(LDAP_PORT_ATTR).orElse(DEFAULT_LDAP_PORT)
      } yield LdapConfig(h, p)
    }.required
}