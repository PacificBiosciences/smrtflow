package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class RegistryService(dao: RegistryDao,
                      actorSystem: ActorSystem,
                      materializer: ActorMaterializer)
    extends SmrtLinkBaseMicroService
    with SmrtLinkJsonProtocols
    with LazyLogging {

  val COMPONENT_ID = toServiceId("registry")
  val COMPONENT_VERSION = "0.2.0"

  val manifest = PacBioComponentManifest(COMPONENT_ID,
                                         "Subsystem Resource Registry Service",
                                         COMPONENT_VERSION,
                                         "Subsystem Resource Registry Service")

  /**
    * Translate the original request to the input for the proxy'ed
    * request.
    *
    * The Host and Timeout-Access headers need to be removed. See
    * {{{HttpRequest}}} for details.
    *
    */
  private def toRequest(originalRequest: HttpRequest,
                        host: String,
                        port: Int,
                        path: Uri.Path): HttpRequest = {
    val uri = Uri.from(scheme = "http", host = host, port = port)
    val px = if (path.startsWithSlash) path else Uri.Path./ ++ path
    val headers =
      originalRequest.headers
        .filter(_.isNot("Timeout-Access".toLowerCase))
        .filter(_.isNot("Host".toLowerCase))
    originalRequest.copy(uri = uri.copy(path = px), headers = headers)
  }

  val routes =
    pathPrefix("registry-service" / "resources") {
      pathEndOrSingleSlash {
        get {
          parameter('resourceId.?) { id =>
            complete {
              Future {
                dao.getResources(id)
              }
            }
          }
        } ~
          post {
            entity(as[RegistryResourceCreate]) { create =>
              complete {
                StatusCodes.Created -> {
                  Future {
                    dao.createResource(create)
                  }
                }
              }
            }
          }
      } ~
        pathPrefix(JavaUUID) { uuid =>
          pathEndOrSingleSlash {
            get {
              complete {
                Future {
                  dao.getResource(uuid)
                }
              }
            } ~
              delete {
                complete {
                  Future {
                    dao.deleteResource(uuid)
                  }
                }
              }
          } ~
            path("update-status") {
              pathEndOrSingleSlash {
                post {
                  complete {
                    Future {
                      dao.updateResource(uuid,
                                         RegistryResourceUpdate(None, None))
                    }
                  }
                }
              }
            } ~
            path("update") {
              pathEndOrSingleSlash {
                post {
                  entity(as[RegistryResourceUpdate]) { update =>
                    complete {
                      for {
                        serverResource <- Future.successful(
                          dao.getResource(uuid))
                        rx <- Future.successful(
                          dao.updateResource(
                            uuid,
                            RegistryResourceUpdate(update.host, update.port)))
                      } yield rx
                    }
                  }
                }
              }
            } ~
            pathPrefix("proxy" / RemainingPath) { path =>
              extractRequestContext { ctx =>
                complete {
                  Future.successful(dao.getResource(uuid)).flatMap { r =>
                    Future {
                      Source
                        .single(toRequest(ctx.request, r.host, r.port, path))
                        .via(Http(actorSystem)
                          .outgoingConnection(r.host, port = r.port))
                        .runWith(Sink.head)(materializer)
                    }
                  }
                }
              }
            }
        }
    }
}

trait RegistryServiceProvider {
  this: RegistryDaoProvider with ActorSystemProvider with ServiceComposer =>

  val registryService: Singleton[RegistryService] =
    Singleton(() =>
      new RegistryService(registryDao(), actorSystem(), actorMaterializer()))

  addService(registryService)
}
