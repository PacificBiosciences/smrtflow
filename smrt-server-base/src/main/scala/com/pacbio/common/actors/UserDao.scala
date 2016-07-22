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
  def regularize(login: String): String = login.trim.toLowerCase

  val ROOT_LOGIN = regularize("root")

  val ROOT_USER = ApiUser(
    login = ROOT_LOGIN,
    id = ROOT_LOGIN,
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

  def deleteUser(login: String): Future[MessageResponse]

  def getToken(login: String): Future[String]
}

/**
 * Concrete implementation of UserDao that stores all user data in memory.
 */
class InMemoryUserDao(defaultRoles: Set[Role], jwtUtils: JwtUtils) extends UserDao {
  import PacBioServiceErrors._
  import UserDao._

  val usersByLogin: mutable.HashMap[String, ApiUser] = new mutable.HashMap()

  override def createUser(login: String, userRecord: UserRecord): Future[ApiUser] = Future {
    val reg = regularize(login)
    if (usersByLogin contains reg)
      throw new UnprocessableEntityError(s"A user with login $reg already exists")
    else {
      val user = ApiUser(
        reg,
        reg, // Use login as id
        userRecord.email,
        userRecord.firstName,
        userRecord.lastName)
        .withPassword(userRecord.password)
        .withRoles(defaultRoles)
      usersByLogin.put(reg, user)
      user
    }
  }

  override def authenticate(login: String, password: String): Future[ApiUser] = Future {
    val reg = regularize(login)
    if (usersByLogin contains reg) {
      val user = usersByLogin(reg)
      if (user.passwordMatches(password))
        user
      else
        throw new UnprocessableEntityError("Invalid credentials")
    }
    else
      throw new ResourceNotFoundError(s"Unable to find user $reg")
  }

  override def getUser(login: String): Future[ApiUser] = Future {
    val reg = regularize(login)
    if (usersByLogin contains reg)
      usersByLogin(reg)
    else
      throw new ResourceNotFoundError(s"Unable to find user $reg")
  }

  override def addRole(login: String, role: Role): Future[ApiUser] = Future {
    val reg = regularize(login)
    if (usersByLogin contains reg) {
      val user = usersByLogin(reg).withRole(role)
      usersByLogin.put(reg, user)
      user
    } else
      throw new ResourceNotFoundError(s"Unable to find user $reg")
  }

  override def removeRole(login: String, role: Role): Future[ApiUser] = Future {
    val reg = regularize(login)
    if (usersByLogin contains reg) {
      val user = usersByLogin(reg).withoutRole(role)
      usersByLogin.put(reg, user)
      user
    } else
      throw new ResourceNotFoundError(s"Unable to find usr $reg")
  }


  override def deleteUser(login: String): Future[MessageResponse] = Future {
    val reg = regularize(login)
    if (usersByLogin contains reg) {
      usersByLogin.remove(reg)
      MessageResponse(s"Successfully deleted user $reg")
    } else
      throw new ResourceNotFoundError(s"Unable to find user $reg")
  }

  override def getToken(login: String): Future[String] = Future {
    val reg = regularize(login)
    if (usersByLogin contains reg)
      jwtUtils.getJwt(usersByLogin(reg))
    else
      throw new ResourceNotFoundError(s"Unable to find user $reg")
  }

  def clear(): Unit = usersByLogin.clear()
}

/**
 * A read-only user DAO that contains one user: root. This is used when auth is disabled.
 */
class RootOnlyUserDao(jwtUtils: JwtUtils) extends UserDao {
  import PacBioServiceErrors._
  import UserDao._

  override def createUser(login: String, userRecord: UserRecord): Future[ApiUser] =
    Future.failed(new MethodNotImplementedError("Cannot create users. Authentication is disabled."))

  // Automatically authenticate as root
  def authenticate(login: String, password: String): Future[ApiUser] = Future.successful(ROOT_USER)

  def getUser(login: String): Future[ApiUser] = Future {
    val reg = regularize(login)
    reg match {
      case ROOT_LOGIN => ROOT_USER
      case _ => throw new ResourceNotFoundError(s"Only user is '$ROOT_LOGIN'. Authentication disabled.")
    }
  }

  def addRole(login: String, role: Role): Future[ApiUser] =
    Future.failed(new MethodNotImplementedError("Cannot add roles. Authentication is disabled."))

  def removeRole(login: String, role: Role): Future[ApiUser] =
    Future.failed(new MethodNotImplementedError("Cannot remove roles. Authentication is disabled."))

  def deleteUser(login: String): Future[MessageResponse] =
    Future.failed(new MethodNotImplementedError("Cannot delete users. Authentication is disabled."))

  def getToken(login: String): Future[String] = Future {
    val reg = regularize(login)
    reg match {
      case ROOT_LOGIN => jwtUtils.getJwt(ROOT_USER)
      case _ => throw new ResourceNotFoundError(s"Only user is '$ROOT_LOGIN'. Authentication disabled.")
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
