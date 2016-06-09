package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.actors.{UserDaoProvider, UserDao}
import com.pacbio.common.auth.{AuthenticatorProvider, Role, ApiUser, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._

// TODO(smcclellan): Add documentation

class UserService(dao: UserDao, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val components = Seq(PacBioComponent(toServiceId("user"), "0.1.0"))

  val manifest = PacBioComponentManifest(
    toServiceId("user"),
    "Subsystem User Service",
    "0.2.0", "Subsystem Health Service", components)

  val userServiceName = "user"

  // Instead of transmitting instances of ApiUser, which contain hashed passwords, this allows us to implicitly convert
  // to UserResponse.
  implicit class ApiUserWrapper(user: Future[ApiUser]) {
    def toResp: Future[UserResponse] =
      user.map(u => UserResponse(u.login, u.id, u.email, u.firstName, u.lastName, u.roles))
  }

  val routes =
    pathPrefix(userServiceName) {
      pathPrefix(Segment) { login =>
        pathEnd {
          put {
            entity(as[UserRecord]) { userRecord =>
              complete {
                created {
                  dao.createUser(login, userRecord).toResp
                }
              }
            }
          } ~
          get {
            complete {
              ok {
                dao.getUser(login).toResp
              }
            }
          } ~
          delete {
            authenticate(authenticator.jwtAuth) { authInfo =>
              authorize(authInfo.isUserOrRoot(login)) {
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
                    ok {
                      dao.deleteUser(login)
                    }
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
                    dao.getToken(login)
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
                        dao.addRole(login, role).toResp
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
                        dao.removeRole(login, role).toResp
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
