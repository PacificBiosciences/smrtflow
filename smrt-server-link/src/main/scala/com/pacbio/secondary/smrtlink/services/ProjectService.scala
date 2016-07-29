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

  def fullProject(proj: Project): Future[FullProject] =
    for {
      datasets <- jobsDao.getDatasetsByProject(proj.id)
      dbUsers <- jobsDao.getProjectUsers(proj.id)
      apiUsers <- Future.sequence(dbUsers.map(u => userDao.getUser(u.login)))
      userResponses = (dbUsers, apiUsers).zipped.map((u, au) =>
        ProjectUserResponse(au.toResponse(), u.role))
    } yield proj.makeFull(datasets, userResponses)

  val routes =
    pathPrefix("projects") {
      authenticate(authenticator.jwtAuth) { authInfo =>
        pathEndOrSingleSlash {
          post {
            entity(as[ProjectRequest]) { sopts =>
              complete {
                created {
                  jobsDao
                    .createProject(sopts, authInfo.login)
                    .flatMap(fullProject)
                }
              }
            }
          } ~
          get {
            complete {
              ok {
                jobsDao.getProjects(1000)
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
                    jobsDao.updateProject(projId, sopts).flatMap {
                      case Some(proj) => fullProject(proj)
                      case None => throw new ResourceNotFoundError(s"Unable to find project $projId")
                    }
                  }
                }
              }
            } ~
            get {
              complete {
                ok {
                  jobsDao.getProjectById(projId).flatMap {
                    case Some(proj) => fullProject(proj)
                    case None => throw new ResourceNotFoundError(s"Unable to find project $projId")
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
