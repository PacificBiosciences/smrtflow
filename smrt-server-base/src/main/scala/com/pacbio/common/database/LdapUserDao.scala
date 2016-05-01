package com.pacbio.common.database

import com.pacbio.common.actors.{UserDaoProvider, UserDao}
import com.pacbio.common.auth.{JwtUtilsProvider, Role, ApiUser, JwtUtils}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.common.services.PacBioServiceErrors
import com.unboundid.ldap.sdk._

import resource._
import scala.collection.mutable

// TODO(smcclellan): Add unit tests
class LdapUserDao(ldapConfig: LdapConfig,
                  ldapUsersConfig: LdapUsersConfig,
                  defaultRoles: Set[Role],
                  jwtUtils: JwtUtils) extends UserDao {

  import PacBioServiceErrors._

  // Used by Cache class to determine what keys should be deleted when the cache is full. The first key to be removed is
  // the key that was touched least recently.
  class DeletionQueue[K](size: Int) {
    var queue: mutable.Queue[K] = new mutable.Queue

    // If the key is already present, touch it and return true, otherwise do nothing and return false.
    def touch(k: K): Boolean = {
      if (queue.contains(k)) {
        queue = queue.filter(!_.equals(k))
        queue.enqueue(k)
        true
      } else false
    }

    // If the key is already present, touch it, otherwise add it. (A new key is considered to be touched most recently.)
    // If a stale key needs to be deleted when the new key is added, the stale key will be returned. Otherwise None is
    // returned.
    def touchOrAdd(k: K): Option[K] = {
      if (touch(k)) {
        None
      } else {
        queue.enqueue(k)
        if (queue.size > size) Some(queue.dequeue()) else None
      }
    }
  }

  // A cache that can store recent responses from the LDAP server, which can be checked if the LDAP server stops
  // functioning correctly. The key-value pair that has been cached or checked least recently will be the first deleted
  // when the cache is full.
  class Cache[Key, Value](size: Int) {
    val cache: mutable.HashMap[Key, Value] = new mutable.HashMap
    val deletionQueue: DeletionQueue[Key] = new DeletionQueue(size)

    // Store a key-value pair in the cache.
    def cache(key: Key, value: Value): Value = {
      cache += key -> value
      for {d <- deletionQueue.touchOrAdd(key)} {cache.remove(d)}
      value
    }

    // Check whether a key is present in the cache. If it is present, return it as the right side of an
    // Either[BaseServiceError, ValueType] object. If it is not present, return the error that triggered the cache check
    // as a Left.
    def check(key: Key, orElse: Throwable): Value = {
      deletionQueue.touch(key)
      val vOpt = cache.get(key)
      if (vOpt.isEmpty) throw orElse
      vOpt.get
    }
  }

  // Maps login -> LDAP entry
  private val entryCache = new Cache[String, ReadOnlyEntry](ldapUsersConfig.cacheSize)

  // Maps (login, password) -> user
  private val authCache = new Cache[(String, String), ApiUser](ldapUsersConfig.cacheSize)

  private def getUserEntry(login: String): ReadOnlyEntry = {
    val connection = managed(new LDAPConnection(ldapConfig.host, ldapConfig.port))
    val entry = connection.acquireAndGet(
      _.searchForEntry(
        ldapUsersConfig.usersDN,
        SearchScope.SUB,
        s"(${ldapUsersConfig.usernameAttr}=$login)",
        ldapUsersConfig.attributes: _*))

    if (entry == null) throw new ResourceNotFoundError(s"Unable to find user $login")

    entryCache.cache(login, entry)
  }

  override def getUser(login: String): ApiUser = ldapUsersConfig.getApiUser(getUserEntry(login)).withRoles(defaultRoles)

  override def authenticate(login: String, password: String): ApiUser = {
    import ResultCode.SUCCESS

    val entry = getUserEntry(login)
    val connection = managed(new LDAPConnection(ldapConfig.host, ldapConfig.port))
    val result = connection.acquireAndGet(_.bind(entry.getAttribute(ldapUsersConfig.CN_ATTR).getValue, password))

    result.getResultCode match {
      case SUCCESS => authCache.cache((login, password), ldapUsersConfig.getApiUser(entry))
      case e       => authCache.check((login, password), new LDAPException(e, s"LDAP Error: ${e.getName}"))
    }
  }

  // TODO(smcclellan): Implement createUser for LDAP?
  override def createUser(login: String, userRecord: UserRecord): ApiUser =
    throw new MethodNotImplementedError("CreateUser method not implemented for LDAP User DAO.")

  // TODO(smcclellan): Implement deleteUser for LDAP?
  override def deleteUser(login: String): String =
      throw new MethodNotImplementedError("DeleteUser method not implemented for LDAP User DAO.")

  // TODO(smcclellan): Implement addRole for LDAP?
  override def addRole(login: String, role: Role): ApiUser =
      throw new MethodNotImplementedError("AddRole method not implemented for LDAP User DAO.")

  // TODO(smcclellan): Implement removeRole for LDAP?
  override def removeRole(login: String, role: Role): ApiUser =
      throw new MethodNotImplementedError("RemoveRole method not implemented for LDAP User DAO.")

  override def getToken(login: String): String = jwtUtils.getJwt(getUser(login))
}

/**
 * Provides a singleton UserDao that interfaces with an LDAP server for the purpose of reading user data and
 * authenticating users. Concrete providers must mixin an LdapUserDaoConfigProvider and a JwtUtilsProvider.
 */
trait LdapUserDaoProvider extends UserDaoProvider {
  this: LdapUserDaoConfigProvider with JwtUtilsProvider =>

  val defaultRoles: Set[Role] = Set()

  override val userDaoImpl: Singleton[UserDao] = Singleton(() =>
    new LdapUserDao(ldapConfig(), ldapUsersConfig(), defaultRoles, jwtUtils()))
}
