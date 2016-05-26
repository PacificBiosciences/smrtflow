package com.pacbio.common.services

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors.{UserServiceActorRefProvider, UserServiceActor}
import com.pacbio.common.auth.{AuthenticatorProvider, Role, ApiUser, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing.Route

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

// TODO(smcclellan): Add documentation

class UserService(userActor: ActorRef, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import UserServiceActor._
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
  implicit class ActorRefWrapper(actorRef: ActorRef) {
    def ??(message: Any): Future[UserResponse] =
      (userActor ? message)
        .mapTo[ApiUser]
        .map(u => UserResponse(u.login, u.id, u.email, u.firstName, u.lastName, u.roles))
  }

  val routes =
    pathPrefix(userServiceName) {
      pathPrefix(Segment) { login =>
        pathEnd {
          put {
            entity(as[UserRecord]) { userRecord =>
              complete {
                created {
                  userActor ?? CreateUser(login, userRecord)
                }
              }
            }
          } ~
          get {
            complete {
              ok {
                userActor ?? GetUser(login)
              }
            }
          } ~
          delete {
            authenticate(authenticator.jwtAuth) { authInfo =>
              authorize(authInfo.isUserOrRoot(login)) {
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
                    ok {
                      (userActor ? DeleteUser(login)).mapTo[String]
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
                    (userActor ? GetToken(login)).mapTo[String]
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
                        userActor ?? AddRole(login, role)
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
                        userActor ?? RemoveRole(login, role)
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
  this: UserServiceActorRefProvider with AuthenticatorProvider =>

  final val userService: Singleton[UserService] =
    Singleton(() => new UserService(userServiceActorRef(), authenticator())).bindToSet(AllServices)
}

trait UserServiceProviderx {
  this: UserServiceActorRefProvider
      with AuthenticatorProvider
      with ServiceComposer =>

  final val userService: Singleton[UserService] =
    Singleton(() => new UserService(userServiceActorRef(), authenticator()))

  addService(userService)
}
