package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.actors.{UserDaoProvider, UserDao}
import com.pacbio.common.auth.{AuthenticatorProvider, ApiUser, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{PacBioComponentManifest, PacBioJsonProtocol, UserResponse}
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.actors.{JobsDaoActorProvider, JobsDaoActor}
import com.pacbio.secondary.smrtlink.models._
import spray.routing.Route

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging

import spray.http.MediaTypes
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


/**
 * Accessing Projects
 *
 * @param dbActor
 */
class ProjectService(dbActor: ActorRef, userDao: UserDao, authenticator: Authenticator)
  extends JobsBaseMicroService
  with SmrtLinkConstants {

  // import message types
  import JobsDaoActor._

  // import serialzation protocols
  import SmrtLinkJsonProtocols._

  val manifest = PacBioComponentManifest(
    toServiceId("smrtlink.project"),
    "SMRT Link Project Service",
    "0.1.0",
    "Project create/read/update")

  val routes =
    pathPrefix("projects") {
      authenticate(authenticator.jwtAuth) { authInfo =>
        pathEndOrSingleSlash {
          post {
            entity(as[ProjectRequest]) { sopts =>
              val owner = ProjectUserRequest(authInfo.login, "OWNER")
              val project = for {
                proj <- (dbActor ? CreateProject(sopts)).mapTo[Project]
                addUser <- (dbActor ? AddProjectUser(proj.id, owner)).mapTo[MessageResponse]
              } yield proj

              complete {
                created {
                  project
                }
              }
            }
          } ~
          get {
            complete {
              ok {
                (dbActor ? GetProjects).mapTo[Seq[Project]]
              }
            }
          }
        } ~
        pathPrefix(IntNumber) { projId =>
          pathEndOrSingleSlash {
            put {
              entity(as[ProjectRequest]) { sopts =>
                complete {
                  ok {
                    (dbActor ? UpdateProject(projId, sopts)).mapTo[Project]
                  }
                }
              }
            } ~
            get {
              complete {
                ok {
                  (dbActor ? GetProjectById(projId)).mapTo[Project]
                }
              }
            }
          } ~
          pathPrefix("users") {
            pathEndOrSingleSlash {
              post {
                entity(as[ProjectUserRequest]) { newUser =>
                  complete {
                    created {
                      (dbActor ? AddProjectUser(projId, newUser)).mapTo[MessageResponse]
                    }
                  }
                }
              } ~
              get {
                complete {
                  ok {
                    for {
                      users <- (dbActor ? GetProjectUsers(projId)).mapTo[Seq[ProjectUser]]
                      apiUsers <- Future.sequence(users.map(u => userDao.getUser(u.login)))
                    } yield (users, apiUsers).zipped.map((u, au) => ProjectUserResponse(au.toResponse(), u.role))
                  }
                }
              }
            } ~
            path(Segment) { userName =>
              delete {
                complete {
                  ok {
                    (dbActor ? DeleteProjectUser(projId, userName)).mapTo[MessageResponse]
                  }
                }
              }
            }
          } ~
          pathPrefix("datasets") {
            get {
              complete {
                ok {
                  (dbActor ? GetDatasetsByProject(projId)).mapTo[Seq[DataSetMetaDataSet]]
                }
              }
            } ~
            path(IntNumber) { dsId =>
              put {
                complete {
                  ok {
                    (dbActor ? SetProjectForDatasetId(dsId, projId)).mapTo[MessageResponse]
                  }
                }
              } ~
              delete {
                complete {
                  ok {
                    (dbActor ? SetProjectForDatasetId(dsId, GENERAL_PROJECT_ID)).mapTo[MessageResponse]
                  }
                }
              }
            } ~
            path(JavaUUID) { dsId =>
              put {
                complete {
                  ok {
                    (dbActor ? SetProjectForDatasetUuid(dsId, projId)).mapTo[MessageResponse]
                  }
                }
              } ~
              delete {
                complete {
                  ok {
                    (dbActor ? SetProjectForDatasetUuid(dsId, GENERAL_PROJECT_ID)).mapTo[MessageResponse]
                  }
                }
              }
            }
          }
        }
      }
    } ~
    path("projects-datasets" / Segment) { login =>
      get {
        complete {
          ok {
            (dbActor ? GetUserProjectsDatasets(login)).mapTo[Seq[ProjectDatasetResponse]]
          }
        }
      }
    } ~
    path("user-projects" / Segment) { login =>
      get {
        complete {
          ok {
            (dbActor ? GetUserProjects(login)).mapTo[Seq[UserProjectResponse]]
          }
        }
      }
    }
}


trait ProjectServiceProvider {
  this: JobsDaoActorProvider
    with UserDaoProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  val projectService: Singleton[ProjectService] =
    Singleton(() => new ProjectService(
      jobsDaoActor(), userDao(), authenticator()))

  addService(projectService)
}
