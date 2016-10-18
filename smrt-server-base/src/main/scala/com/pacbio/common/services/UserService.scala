package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

class UserService(authenticator: Authenticator) extends PacBioService {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("user"),
    "User Service",
    "0.1.0", "User Service")

  val routes =
    path("user") {
      authenticate(authenticator.wso2Auth) { user =>
        get {
          complete {
            ok {
              user
            }
          }
        }
      }
    }
}

/**
 * Provides a singleton UserService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{StatusServiceActorRefProvider}}}.
 */
trait UserServiceProvider {
  this: AuthenticatorProvider =>

  val userService: Singleton[UserService] = Singleton(() => new UserService(authenticator())).bindToSet(AllServices)
}

trait UserServiceProviderx {
  this: AuthenticatorProvider with ServiceComposer =>

  val userService: Singleton[UserService] = Singleton(() => new UserService(authenticator()))

  addService(userService)
}
