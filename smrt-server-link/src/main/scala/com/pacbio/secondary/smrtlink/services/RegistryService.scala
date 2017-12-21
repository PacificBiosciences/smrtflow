package com.pacbio.secondary.smrtlink.services

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{
  RegistryServiceActor,
  RegistryServiceActorRefProvider
}
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{
  HttpEntity,
  HttpHeader,
  HttpMethod,
  StatusCodes,
  Uri,
  HttpResponse => SprayHttpResponse
}
import akka.http.scaladsl.model.headers.RawHeader

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

//MK. Why are we using this data model?
import scalaj.http.HttpResponse

class RegistryService(registryActor: ActorRef)
    extends SmrtLinkBaseMicroService
    with SmrtLinkJsonProtocols {

  import RegistryServiceActor._

  val COMPONENT_ID = toServiceId("registry")
  val COMPONENT_VERSION = "0.1.1"

  val manifest = PacBioComponentManifest(COMPONENT_ID,
                                         "Subsystem Resource Registry Service",
                                         COMPONENT_VERSION,
                                         "Subsystem Resource Registry Service")

  val routes =
    //authenticate(authenticator.wso2Auth) { user =>
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
              complete {
                StatusCodes.Created -> {
                  (registryActor ? CreateResource(create))
                    .mapTo[RegistryResource]
                }
              }
            }
          }
      } ~
        pathPrefix(JavaUUID) { uuid =>
          pathEnd {
            get {
              complete {
                (registryActor ? GetResource(uuid)).mapTo[RegistryResource]
              }
            } ~
              delete {
                complete {
                  (registryActor ? DeleteResource(uuid))
                    .mapTo[MessageResponse]
                }
              }
          } ~
            path("update-status") {
              post {
                complete {
                  (registryActor ? UpdateResource(
                    uuid,
                    RegistryResourceUpdate(None, None)))
                    .mapTo[RegistryResource]
                }
              }
            } ~
            path("update") {
              post {
                entity(as[RegistryResourceUpdate]) { update =>
                  complete {
                    (registryActor ? UpdateResource(uuid, update))
                      .mapTo[RegistryResource]
                  }
                }
              }
            } ~
            pathPrefix("proxy") {
              extract[HttpMethod](_.request.method) { method =>
                extract[HttpEntity](_.request.entity) { ent =>
                  extract[Seq[HttpHeader]](_.request.headers) { headers =>
                    parameterMap { params =>
                      path(RemainingPath) { path =>
                        complete {
                          handleProxy(uuid,
                                      path,
                                      method,
                                      ent,
                                      headers.toList,
                                      params)
                        }
                      } ~
                        pathEnd {
                          complete {
                            handleProxy(uuid,
                                        Uri.Path("/"),
                                        method,
                                        ent,
                                        headers.toList,
                                        params)
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
  def handleProxy(uuid: UUID,
                  pth: Uri.Path,
                  meth: HttpMethod,
                  ent: HttpEntity,
                  head: List[HttpHeader],
                  par: Map[String, String]): Future[SprayHttpResponse] = {

    // FIXME. This should consistently use the Uri.Path type
    val path: String =
      if (pth.toString() startsWith "/") pth.toString()
      else "/" + pth.toString()

    val headers: Option[Map[String, String]] =
      if (head isEmpty) None else Some(head.map(h => h.name -> h.value).toMap)
    val params: Option[Map[String, String]] =
      if (par isEmpty) None else Some(par)

    //val data: Option[Array[Byte]] = ent.toOption.map(_.data.toByteArray)
    //val req = RegistryProxyRequest(path, method, data, headers, params)

    for {
      dx <- ent.toStrict(1.second).map(_.data)
      req <- Future.successful(
        RegistryProxyRequest(path,
                             meth.value,
                             Option(dx.toArray),
                             headers,
                             params))
      r <- (registryActor ? ProxyRequest(uuid, req))
        .mapTo[HttpResponse[Array[Byte]]]
      hx <- Future.successful(
        r.headers
          .map { case (n, vs) => vs.map(v => RawHeader(n, v)).toList }
          .toList
          .flatten)
    } yield
      SprayHttpResponse(
        status = StatusCodes.getForKey(r.code).get,
        entity = HttpEntity.apply(r.body),
        headers = hx
      )

  }
}

trait RegistryServiceProvider {
  this: RegistryServiceActorRefProvider with ServiceComposer =>

  val registryService: Singleton[RegistryService] =
    Singleton(() => new RegistryService(registryServiceActorRef()))

  addService(registryService)
}
