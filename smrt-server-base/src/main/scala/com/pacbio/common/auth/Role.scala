package com.pacbio.common.auth

import scala.collection.mutable

object Role {
  val repo: mutable.HashMap[String, Role] = new mutable.HashMap

  def fromString(roleName: String): Option[Role] = Role.repo.get(roleName)
}

/**
 * Super-type for all roles.
 */
sealed trait Role {
  /**
   * Enforces a partial ordering over roles.
   */
  def >=(role: Role): Boolean

  /**
   * Registers this role with the global repo. Should be called on all newly defined roles.
   */
  def register(): Unit = Role.repo.put(this.toString, this)
}

/**
 * Abstract implementation of Role that implements ordering via a set of sub-roles. Services that wish to create new
 * roles should do so by extending this class.
 */
abstract class AbstractRole(val sub: Set[Role] = Set.empty) extends Role {
  /**
   * A >= B iff A == B || X >= B, for some X in A.sub
   */
  final override def >=(role: Role): Boolean = {
    if (role == this) true
    else if (sub.isEmpty) false
    else sub.exists(_ >= role)
  }
}

/**
 * Contains roles for interacting with the Base SMRT server. Systems that wish to define their own roles, should do so
 * in a similar way, by creating case objects that extend AbstractRole. E.g.:
 *
 * {{{
 *   object MySubsystemRoles {
 *     import com.pacbio.common.auth.AbstractRole
 *
 *     case object MY_SUBSYSTEM_BLUE_ROLE extends AbstractRole
 *     case object MY_SUBSYSTEM_RED_ROLE extends AbstractRole
 *     case object MY_SUBSYSTEM_ADMIN_ROLE extends AbstractRole(Set(MY_SUBSYSTEM_BLUE_ROLE, MY_SUBSYSTEM_RED_ROLE))
 *   }
 * }}}
 */
object BaseRoles {
  case object ROOT extends Role {
    override def >=(role: Role): Boolean = true
  }
  case object ADMIN extends Role {
    override def >=(role: Role): Boolean = if (role == ROOT) false else true
  }

  case object HEALTH_AND_LOGS_WRITE extends AbstractRole()
  case object HEALTH_AND_LOGS_ADMIN extends AbstractRole(Set(HEALTH_AND_LOGS_WRITE))

  case object CLEANUP_ADMIN extends AbstractRole()
}

/**
 * Trait that registers base roles with the global repo. This trait should be mixed in with the top-level App to ensure
 * that all roles are properly registered at start-up. Systems that wish to define their own roles should create a
 * similar trait to be mixed in in the same way. E.g.:
 *
 * {{{
 *   trait MySubsystemRolesInit {
 *     import MySubsystemRoles._
 *
 *     MY_SUBSYSTEM_BLUE_ROLE.register()
 *     MY_SUBSYSTEM_RED_ROLE.register()
 *     MY_SUBSYSTEM_ADMIN_ROLE.register()
 *
 * }}}
 */
trait BaseRolesInit {
  import BaseRoles._

  ROOT.register()
  ADMIN.register()
  HEALTH_AND_LOGS_ADMIN.register()
  HEALTH_AND_LOGS_WRITE.register()
  CLEANUP_ADMIN.register()
}
