package com.pacbio.common.actors

import akka.actor.{Props, ActorRef, Actor}
import com.pacbio.common.auth.Role
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord

/**
 * Companion object for the UserServiceActor class, defining the set of messages it can handle.
 */
object UserServiceActor {
  case class CreateUser(login: String, userRecord: UserRecord)
  case class GetUser(login: String)
  case class AddRole(login: String, role: Role)
  case class RemoveRole(login: String, role: Role)
  case class DeleteUser(login: String)
  case class GetToken(login: String)
}

/**
 * Akka actor that wraps a UserDao.
 */
class UserServiceActor(userDao: UserDao) extends PacBioActor {

  import UserServiceActor._

  def receive: Receive = {
    case CreateUser(login: String, userRecord: UserRecord) => respondWith(userDao.createUser(login, userRecord))
    case GetUser(login: String)                            => respondWith(userDao.getUser(login))
    case AddRole(login: String, role: Role)                => respondWith(userDao.addRole(login, role))
    case RemoveRole(login: String, role: Role)             => respondWith(userDao.removeRole(login, role))
    case DeleteUser(login: String)                         => respondWith(userDao.deleteUser(login))
    case GetToken(login: String)                           => respondWith(userDao.getToken(login))
  }
}

/**
 * Provides a singleton ActorRef for a UserServiceActor. Concrete providers must mixin a UserDaoProvider and an
 * ActorRefFactoryProvider.
 */
trait UserServiceActorRefProvider {
  this: UserDaoProvider with ActorRefFactoryProvider =>

  final val userServiceActorRef: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[UserServiceActor], userDao()), "UserServiceActor"))
}

/**
 * Provides a singleton UserServiceActor. Concrete providers must mixin a UserDaoProvider. Note that this provider is
 * designed for tests, and should generally not be used in production. To create a production app, use the
 * {{{UserServiceActorRefProvider}}}.
 */
trait UserServiceActorProvider {
  this: UserDaoProvider =>

  final val userServiceActor: Singleton[UserServiceActor] = Singleton(() => new UserServiceActor(userDao()))
}

