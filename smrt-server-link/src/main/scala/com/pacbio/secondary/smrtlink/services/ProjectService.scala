package com.pacbio.secondary.smrtlink.services

import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.PacBioServiceErrors.{ConflictError, ResourceNotFoundError}
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
class ProjectService(jobsDao: JobsDao, authenticator: Authenticator)
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
      userResponses <- Future(dbUsers.map(u => ProjectUserResponse(u.login, u.role)))
    } yield proj.makeFull(datasets, userResponses)

  def maybeFullProject(projId: Int): Option[Project] => Future[FullProject] = {
    {
      case Some(proj) => fullProject(proj)
      case None => throw new ResourceNotFoundError(s"Unable to find project $projId")
    }
  }

  def translateConflict(sopts: ProjectRequest): PartialFunction[Throwable, Future[FullProject]] = {
    {
      case ex: Throwable if jobsDao.isConstraintViolation(ex) =>
        Future.failed(new ConflictError(s"There is already a project named ${sopts.name}"))
    }
  }

  val routes =
    pathPrefix("projects") {
      pathEndOrSingleSlash {
        post {
          authenticate(authenticator.wso2Auth) { user =>
            entity(as[ProjectRequest]) { sopts =>
              complete {
                created {
                  jobsDao
                    .createProject(sopts, user.userId)
                    .flatMap(fullProject)
                    .recoverWith(translateConflict(sopts))
                }
              }
            }
          }
        } ~
        get {
          authenticate(authenticator.wso2Auth) { user =>
            complete {
              ok {
                jobsDao.getProjects(1000)
              }
            }
          }
        }
      } ~
      pathPrefix(IntNumber) { projId =>
        pathEndOrSingleSlash {
          put {
            authenticate(authenticator.wso2Auth) { user =>
              entity(as[ProjectRequest]) { sopts =>
                complete {
                  ok {
                    jobsDao
                      .updateProject(projId, sopts)
                      .flatMap(maybeFullProject(projId))
                      .recoverWith(translateConflict(sopts))
                  }
                }
              }
            }
          } ~
          get {
            authenticate(authenticator.wso2Auth) { user =>
              complete {
                ok {
                  jobsDao
                    .getProjectById(projId)
                    .flatMap(maybeFullProject(projId))
                }
              }
            }
          } ~
          delete {
            authenticate(authenticator.wso2Auth) { user =>
              // TODO(mskinner) when we bring back project membership,
              // only allow authorized users (OWNER, CAN_EDIT?) to
              // delete a project
              complete {
                ok {
                  jobsDao
                    .deleteProjectById(projId)
                }
              }
            }
          }
        }
      }
    } ~
    path("projects-datasets" / Segment) { login =>
      get {
        authenticate(authenticator.wso2Auth) { user =>
          complete {
            ok {
              jobsDao.getUserProjectsDatasets(login)
            }
          }
        }
      }
    } ~
    path("user-projects" / Segment) { login =>
      get {
        authenticate(authenticator.wso2Auth) { user =>
          complete {
            ok {
              jobsDao.getUserProjects(login)
            }
          }
        }
      }
    }
}


trait ProjectServiceProvider {
  this: JobsDaoProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  val projectService: Singleton[ProjectService] =
    Singleton(() => new ProjectService(jobsDao(), authenticator()))

  addService(projectService)
}
