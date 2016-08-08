package com.pacbio.secondary.smrtlink.services

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.auth.Authenticator
import com.pacbio.common.auth.AuthenticatorProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{RegistryServiceActorRefProvider, RegistryServiceActor}
import com.pacbio.secondary.smrtlink.auth.SmrtLinkRoles
import com.pacbio.secondary.smrtlink.models._
import spray.http.{HttpResponse => SprayHttpResponse, HttpHeader, HttpEntity, HttpMethod}
import spray.httpx.SprayJsonSupport._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.language.postfixOps
import scalaj.http.HttpResponse

class RegistryService(registryActor: ActorRef, authenticator: Authenticator)
  extends SmrtLinkBaseMicroService with SmrtLinkJsonProtocols {

  import RegistryServiceActor._
  import SmrtLinkRoles._

  val COMPONENT_ID = toServiceId("registry")
  val COMPONENT_VERSION = "0.1.1"

  val manifest = PacBioComponentManifest(
    COMPONENT_ID,
    "Subsystem Resource Registry Service",
    COMPONENT_VERSION, "Subsystem Resource Registry Service")

  val routes =
    //authenticate(authenticator.jwtAuth) { authInfo =>
      pathPrefix("registry-service" / "resources") {
        pathEnd {
          get {
            parameter('resourceId.?) { id =>
              complete {
                (registryActor ? GetResources(id)).mapTo[Set[RegistryResource]]
              }
            }
          } ~
          post {
            entity(as[RegistryResourceCreate]) { create =>
              //authorize(authInfo.hasPermission(REGISTRY_WRITE)) {
                complete {
                  created {
                    (registryActor ? CreateResource(create)).mapTo[RegistryResource]
                  }
                }
              //}
            }
          }
        } ~
        pathPrefix(JavaUUID) { uuid =>
          pathEnd {
            get {
              complete {
                ok {
                  (registryActor ? GetResource(uuid)).mapTo[RegistryResource]
                }
              }
            } ~
            delete {
              complete {
                ok {
                  (registryActor ? DeleteResource(uuid)).mapTo[MessageResponse]
                }
              }
            }
          } ~
          path("update-status") {
            post {
              complete {
                ok {
                  (registryActor ? UpdateResource(uuid, RegistryResourceUpdate(None, None))).mapTo[RegistryResource]
                }
              }
            }
          } ~
          path("update") {
            post {
              entity(as[RegistryResourceUpdate]) { update =>
                //authorize(authInfo.hasPermission(REGISTRY_WRITE)) {
                  complete {
                    ok {
                      (registryActor ? UpdateResource(uuid, update)).mapTo[RegistryResource]
                    }
                  }
                //}
              }
            }
          } ~
          pathPrefix("proxy") {
            extract[HttpMethod](_.request.method) { method =>
              extract[HttpEntity](_.request.entity) { ent =>
                extract[List[HttpHeader]](_.request.headers) { headers =>
                  parameterMap { params =>
                    path(Rest) { path =>
                      complete {
                        handleProxy(uuid, path, method, ent, headers, params)
                      }
                    } ~
                    pathEnd {
                      complete {
                        handleProxy(uuid, "", method, ent, headers, params)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    //}

  // TODO(smcclellan): Do we need to handle cookies?
  def handleProxy(
      uuid: UUID,
      pth: String,
      meth: HttpMethod,
      ent: HttpEntity,
      head: List[HttpHeader],
      par: Map[String, String]): Future[SprayHttpResponse] = {
    import spray.http.{HttpEntity, StatusCodes}
    import spray.http.HttpHeaders.RawHeader

    val path: String = if (pth startsWith "/") pth else "/" + pth
    val method: String = meth.value
    val data: Option[Array[Byte]] = ent.toOption.map(_.data.toByteArray)
    val headers: Option[Map[String, String]] = if (head isEmpty) None else Some(head.map(h => h.name -> h.value).toMap)
    val params: Option[Map[String, String]] = if (par isEmpty) None else Some(par)

    val req = RegistryProxyRequest(path, method, data, headers, params)

    val resp = (registryActor ? ProxyRequest(uuid, req)).mapTo[HttpResponse[Array[Byte]]]

    resp.map { r =>
      SprayHttpResponse(
        StatusCodes.getForKey(r.code).get,
        HttpEntity.apply(r.body),
        r.headers.map { case (n, v) => RawHeader(n, v) } toList
      )
    }
  }
}

trait RegistryServiceProvider {
  this: RegistryServiceActorRefProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  val registryService: Singleton[RegistryService] =
    Singleton(() => new RegistryService(registryServiceActorRef(), authenticator()))

  addService(registryService)
}
