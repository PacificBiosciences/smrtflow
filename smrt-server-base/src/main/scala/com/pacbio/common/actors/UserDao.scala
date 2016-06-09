package com.pacbio.common.actors

import com.pacbio.common.auth.BaseRoles._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

object UserDao {
  val ROOT_USER = ApiUser(
    login = "root",
    id = "root",
    email = None,
    firstName = None,
    lastName = None,
    roles = Set(ROOT))
}

/**
 * Interface for the User Service DAO.
 */
trait UserDao {
  def createUser(login: String, userRecord: UserRecord): Future[ApiUser]

  // Note: this is not exposed by the UserServiceActor, and should only be used by the Authenticator
  def authenticate(login: String, password: String): Future[ApiUser]

  def getUser(login: String): Future[ApiUser]

  def addRole(login: String, role: Role): Future[ApiUser]

  def removeRole(login: String, role: Role): Future[ApiUser]

  def deleteUser(login: String): Future[String]

  def getToken(login: String): Future[String]
}

/**
 * Concrete implementation of UserDao that stores all user data in memory.
 */
class InMemoryUserDao(defaultRoles: Set[Role], jwtUtils: JwtUtils) extends UserDao {
  import PacBioServiceErrors._

  val usersByLogin: mutable.HashMap[String, ApiUser] = new mutable.HashMap()

  override def createUser(login: String, userRecord: UserRecord): Future[ApiUser] = Future {
    if (usersByLogin contains login)
      throw new UnprocessableEntityError(s"A user with login $login already exists")
    else {
      val user = ApiUser(
        login,
        login, // Use login as id
        userRecord.email,
        userRecord.firstName,
        userRecord.lastName)
          .withPassword(userRecord.password)
          .withRoles(defaultRoles)
      usersByLogin.put(login, user)
      user
    }
  }

  override def authenticate(login: String, password: String): Future[ApiUser] = Future {
    if (usersByLogin contains login) {
      val user = usersByLogin(login)
      if (user.passwordMatches(password))
        user
      else
        throw new UnprocessableEntityError("Invalid credentials")
    }
    else
      throw new ResourceNotFoundError(s"Unable to find user $login")
  }

  override def getUser(login: String): Future[ApiUser] = Future {
    if (usersByLogin contains login)
      usersByLogin(login)
    else
      throw new ResourceNotFoundError(s"Unable to find user $login")
  }

  override def addRole(login: String, role: Role): Future[ApiUser] = Future {
    if (usersByLogin contains login) {
      val user = usersByLogin(login).withRole(role)
      usersByLogin.put(login, user)
      user
    } else
      throw new ResourceNotFoundError(s"Unable to find user $login")
  }

  override def removeRole(login: String, role: Role): Future[ApiUser] = Future {
    if (usersByLogin contains login) {
      val user = usersByLogin(login).withoutRole(role)
      usersByLogin.put(login, user)
      user
    } else
      throw new ResourceNotFoundError(s"Unable to find user $login")
  }

  override def deleteUser(login: String): Future[String] = Future {
    if (usersByLogin contains login) {
      usersByLogin.remove(login)
      s"Successfully deleted user $login"
    } else
      throw new ResourceNotFoundError(s"Unable to find user $login")
  }

  override def getToken(login: String): Future[String] = Future {
    if (usersByLogin contains login)
      jwtUtils.getJwt(usersByLogin(login))
    else
      throw new ResourceNotFoundError(s"Unable to find user $login")
  }

  def clear(): Unit = usersByLogin.clear()
}

/**
 * A read-only user DAO that contains one user: root. This is used when auth is disabled.
 */
class RootOnlyUserDao(jwtUtils: JwtUtils) extends UserDao {
  import PacBioServiceErrors._
  import UserDao.ROOT_USER

  override def createUser(login: String, userRecord: UserRecord): Future[ApiUser] =
    Future.failed(new MethodNotImplementedError("Cannot create users. Authentication is disabled."))

  // Automatically authenticate as root
  def authenticate(login: String, password: String): Future[ApiUser] = Future.successful(ROOT_USER)

  def getUser(login: String): Future[ApiUser] = Future {
    login match {
      case ROOT_USER.login => ROOT_USER
      case _ => throw new ResourceNotFoundError("Only user is 'root'. Authentication disabled.")
    }
  }

  def addRole(login: String, role: Role): Future[ApiUser] =
    Future.failed(new MethodNotImplementedError("Cannot add roles. Authentication is disabled."))

  def removeRole(login: String, role: Role): Future[ApiUser] =
    Future.failed(new MethodNotImplementedError("Cannot remove roles. Authentication is disabled."))

  def deleteUser(login: String): Future[String] =
    Future.failed(new MethodNotImplementedError("Cannot delete users. Authentication is disabled."))

  def getToken(login: String): Future[String] = Future {
    login match {
      case ROOT_USER.login => jwtUtils.getJwt(ROOT_USER)
      case _ => throw new ResourceNotFoundError("Only user is 'root'. Authentication disabled.")
    }
  }
}

/**
 * Abstract provider for injecting a singleton UserDao. Concrete providers must override the userDaoImpl val.
 */
trait UserDaoProvider extends EnableAuthenticationConfig {
  this: JwtUtilsProvider =>

  val userDaoImpl: Singleton[UserDao]

  final val userDao: Singleton[UserDao] = Singleton(() => enableAuthentication() match {
    case true => userDaoImpl()
    case false => new RootOnlyUserDao(jwtUtils())
  })
}

/**
 * Provides a singleton InMemoryUserDao. Concrete providers must mixin a JwtUtilsProvider.
 */
trait InMemoryUserDaoProvider extends UserDaoProvider {
  this: JwtUtilsProvider =>

  def defaultRoles: Set[Role] = Set(ADMIN)

  override val userDaoImpl: Singleton[UserDao] = Singleton(() => new InMemoryUserDao(defaultRoles, jwtUtils()))
}
