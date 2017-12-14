package com.pacbio.secondary.smrtlink.services

import akka.util.Timeout
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.auth.{
  Authenticator,
  AuthenticatorProvider
}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.pacbio.secondary.smrtlink.services.utils.SmrtDirectives

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class UserService() extends PacBioService {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(toServiceId("user"),
                                         "User Service",
                                         "0.1.0",
                                         "User Service")

  val userRoute =
    path("user") {
      SmrtDirectives.extractRequiredUserRecord { user =>
        get {
          complete {
            ok {
              user
            }
          }
        }
      }
    }

  val routes = userRoute ~ pathPrefix("smrt-link") { userRoute }
}

/**
  * Provides a singleton UserService, and also binds it to the set of total services. Concrete providers must mixin a
  * {{{StatusServiceActorRefProvider}}}.
  */
trait UserServiceProvider { this: AuthenticatorProvider =>

  val userService: Singleton[UserService] =
    Singleton(() => new UserService()).bindToSet(AllServices)
}

trait UserServiceProviderx {
  this: AuthenticatorProvider with ServiceComposer =>

  val userService: Singleton[UserService] = Singleton(() => new UserService())

  addService(userService)
}
