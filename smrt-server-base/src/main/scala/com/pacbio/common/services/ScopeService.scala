package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.auth.{AuthenticatorProvider, AuthInfo, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

class ScopeService(authenticator: Authenticator) extends PacBioService {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("scope"),
    "Scope Service",
    "0.1.0", "Scope Service")

  val routes =
    pathPrefix("scope") {
      path(Segment) { s =>
        get {
          complete {
            noContent
          }
        }
      }
    } ~
    path("role" / "Internal" / Segment) { r =>
      val role = Roles.fromString(s"Internal/$r")
        .getOrElse(throw new ResourceNotFoundError(s"No role named 'Internal/$r'"))
      get {
        authenticate(authenticator.wso2Auth) {authInfo: AuthInfo =>
          authorize(authInfo.hasPermission(role)) {
            complete {
              noContent
            }
          }
        }
      }
    }
}

/**
 * Provides a singleton ScopeService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{AuthenticatorProvider}}}.
 */
trait ScopeServiceProvider {
  this: AuthenticatorProvider =>

  val scopeService: Singleton[ScopeService] =
    Singleton(() => new ScopeService(authenticator())).bindToSet(AllServices)
}

trait ScopeServiceProviderx {
  this: AuthenticatorProvider with ServiceComposer =>

  val scopeService: Singleton[ScopeService] =
    Singleton(() => new ScopeService(authenticator()))

  addService(scopeService)
}
