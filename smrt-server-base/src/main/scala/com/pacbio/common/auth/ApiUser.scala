package com.pacbio.common.auth

import com.github.t3hnar.bcrypt._

/**
 * Represents a user, including a login, a cryptographically hashed password, and a set of roles this user can act as.
 *
 * To ensure that the un-hashed password is never stored, ApiUser objects should be created like this:
 * {{{
 *   ApiUser user = User("username", "id", "jsmith@foo.com", "John", "Smith").withPassword("password")
 * }}}
 *
 * The password can be tested later as follows:
 * {{{
 *   user.passwordMatches("password") // returns true
 *   user.passwordMatches("foobar") // returns false
 * }}}
 */
case class ApiUser(login: String,
                   id: String,
                   email: Option[String],
                   firstName: Option[String],
                   lastName: Option[String],
                   hashedPassword: Option[String] = None,
                   roles: Set[Role] = Set.empty) {

  def withPassword(password: String): ApiUser = copy(hashedPassword = Some(password.bcrypt(10)))

  def passwordMatches(password: String): Boolean =
    hashedPassword.exists(hash => password.isBcrypted(hash))

  def withRole(newRole: Role): ApiUser = copy(roles = roles + newRole)

  def withRoles(newRoles: Set[Role]): ApiUser = copy(roles = roles ++ newRoles)

  def withoutRole(oldRole: Role): ApiUser = copy(roles = roles - oldRole)
}