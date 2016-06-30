package com.pacbio.common.services

import com.pacbio.common.actors.{UserDaoProvider, UserDao}
import com.pacbio.common.auth.{AuthenticatorProvider, Role, ApiUser, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

// TODO(smcclellan): Add documentation

class UserService(userDao: UserDao, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import PacBioJsonProtocol._

  val manifest = PacBioComponentManifest(
    toServiceId("user"),
    "Subsystem User Service",
    "0.2.0", "Subsystem Health Service")

  val userServiceName = "user"

  // Instead of transmitting instances of ApiUser, which contain hashed passwords, this allows us to implicitly convert
  // to UserResponse.
  implicit class ApiUserWrapper(apiUser: Future[ApiUser]) {
    def resp: Future[UserResponse] =
      apiUser.map(u => UserResponse(u.login, u.id, u.email, u.firstName, u.lastName, u.roles))
  }

  val routes =
    pathPrefix(userServiceName) {
      pathPrefix(Segment) { login =>
        pathEnd {
          put {
            entity(as[UserRecord]) { userRecord =>
              complete {
                created {
                  userDao.createUser(login, userRecord).resp
                }
              }
            }
          } ~
          get {
            complete {
              ok {
                userDao.getUser(login).resp
              }
            }
          } ~
          delete {
            authenticate(authenticator.jwtAuth) { authInfo =>
              authorize(authInfo.isUserOrRoot(login)) {
                complete {
                  ok {
                    userDao.deleteUser(login)
                  }
                }
              }
            }
          }
        } ~
        path("token") {
          get {
            authenticate(authenticator.userPassAuth) { authInfo =>
              authorize(authInfo.isUserOrRoot(login)) {
                complete {
                  ok {
                    userDao.getToken(login)
                  }
                }
              }
            }
          }
        } ~
        pathPrefix("role") {
          authenticate(authenticator.jwtAuth) { authInfo =>
            path("add") {
              post {
                entity(as[String]) { r =>
                  val role = Role.repo(r)
                  authorize(authInfo.hasPermission(role)) {
                    complete {
                      ok {
                        userDao.addRole(login, role).resp
                      }
                    }
                  }
                }
              }
            } ~
            path("remove") {
              post {
                entity(as[String]) { r =>
                  val role = Role.repo(r)
                  authorize(authInfo.hasPermission(role)) {
                    complete {
                      ok {
                        userDao.removeRole(login, role).resp
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
}

/**
 * Provides a singleton UserService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{UserServiceActorRefProvider}}} and an {{{AuthenticatorProvider}}}.
 */
trait UserServiceProvider {
  this: UserDaoProvider with AuthenticatorProvider =>

  final val userService: Singleton[UserService] =
    Singleton(() => new UserService(userDao(), authenticator())).bindToSet(AllServices)
}

trait UserServiceProviderx {
  this: UserDaoProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  final val userService: Singleton[UserService] =
    Singleton(() => new UserService(userDao(), authenticator()))

  addService(userService)
}
