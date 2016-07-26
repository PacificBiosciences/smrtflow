package com.pacbio.secondary.smrtlink.services

import com.pacbio.common.actors.{UserDao, UserDaoProvider}
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.actors.{JobsDaoProvider, JobsDao}
import com.pacbio.secondary.smrtlink.models._
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Accessing Projects
 */
class ProjectService(jobsDao: JobsDao, userDao: UserDao, authenticator: Authenticator)
  extends JobsBaseMicroService
  with SmrtLinkConstants {

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
                proj <- jobsDao.createProject(sopts)
                addUser <- jobsDao.addProjectUser(proj.id, owner)
              } yield proj

              complete {
                created {
                  project
                }
              }
            }
          } ~
          get {
            pageParams { (limit, offset) =>
              complete {
                ok {
                  jobsDao.getProjects(limit, offset)
                }
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
                    jobsDao.updateProject(projId, sopts)
                      .map(_.getOrElse(throw new ResourceNotFoundError(s"Unable to find project $projId")))
                  }
                }
              }
            } ~
            get {
              complete {
                ok {
                  jobsDao.getProjectById(projId)
                    .map(_.getOrElse(throw new ResourceNotFoundError(s"Unable to find project $projId")))
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
                      jobsDao.addProjectUser(projId, newUser)
                    }
                  }
                }
              } ~
              get {
                complete {
                  ok {
                    for {
                      users <- jobsDao.getProjectUsers(projId)
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
                    jobsDao.deleteProjectUser(projId, userName)
                  }
                }
              }
            }
          } ~
          pathPrefix("datasets") {
            get {
              complete {
                ok {
                  jobsDao.getDatasetsByProject(projId)
                }
              }
            } ~
            path(IntNumber) { dsId =>
              put {
                complete {
                  ok {
                    jobsDao.setProjectForDatasetId(dsId, projId)
                  }
                }
              } ~
              delete {
                complete {
                  ok {
                    jobsDao.setProjectForDatasetId(dsId, GENERAL_PROJECT_ID)
                  }
                }
              }
            } ~
            path(JavaUUID) { dsId =>
              put {
                complete {
                  ok {
                    jobsDao.setProjectForDatasetUuid(dsId, projId)
                  }
                }
              } ~
              delete {
                complete {
                  ok {
                    jobsDao.setProjectForDatasetUuid(dsId, GENERAL_PROJECT_ID)
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
            jobsDao.getUserProjectsDatasets(login)
          }
        }
      }
    } ~
    path("user-projects" / Segment) { login =>
      get {
        complete {
          ok {
            jobsDao.getUserProjects(login)
          }
        }
      }
    }
}


trait ProjectServiceProvider {
  this: JobsDaoProvider
    with UserDaoProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  val projectService: Singleton[ProjectService] =
    Singleton(() => new ProjectService(jobsDao(), userDao(), authenticator()))

  addService(projectService)
}
