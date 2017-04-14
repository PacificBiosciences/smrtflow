package com.pacbio.secondary.smrtlink.services

import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.PacBioServiceErrors.{ConflictError, ResourceNotFoundError}
import com.pacbio.common.services.utils.FutureSecurityDirectives
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
  with FutureSecurityDirectives
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
      userResponses <- Future(dbUsers.map(u => ProjectRequestUser(u.login, u.role)))
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

  private val readRoles: Set[ProjectUserRole.ProjectUserRole] =
    Set(ProjectUserRole.OWNER, ProjectUserRole.CAN_EDIT, ProjectUserRole.CAN_VIEW)

  private val writeRoles: Set[ProjectUserRole.ProjectUserRole] =
    Set(ProjectUserRole.OWNER, ProjectUserRole.CAN_EDIT)

  private val ownerRole: Set[ProjectUserRole.ProjectUserRole] =
    Set(ProjectUserRole.OWNER)

  private def userCanRead(login: String, projectId: Int) =
    jobsDao.userHasProjectRole(login, projectId, readRoles)

  private def userCanWrite(login: String, projectId: Int) =
    jobsDao.userHasProjectRole(login, projectId, writeRoles)

  private def userIsOwner(login: String, projectId: Int) =
    jobsDao.userHasProjectRole(login, projectId, ownerRole)

  val routes =
    pathPrefix("projects") {
      pathEndOrSingleSlash {
        post {
          authenticate(authenticator.wso2Auth) { user =>
            entity(as[ProjectRequest]) { sopts =>
              complete {
                validateUpdates(sopts)
                created {
                  jobsDao
                    .createProject(sopts)
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
                jobsDao.getUserProjects(user.userId).map(_.map(_.project))
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
                futureAuthorize(userCanWrite(user.userId, projId)) {
                  complete {
                    ok {
                      validateUpdates(sopts)
                      jobsDao
                        .updateProject(projId, sopts)
                        .flatMap(maybeFullProject(projId))
                        .recoverWith(translateConflict(sopts))
                    }
                  }
                }
              }
            }
          } ~
          get {
            authenticate(authenticator.wso2Auth) { user =>
              futureAuthorize(userCanRead(user.userId, projId)) {
                complete {
                  ok {
                    jobsDao
                      .getProjectById(projId)
                      .flatMap(maybeFullProject(projId))
                  }
                }
              }
            }
          } ~
          delete {
            authenticate(authenticator.wso2Auth) { user =>
              futureAuthorize(userIsOwner(user.userId, projId)) {
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

  def validateUpdates(updates: ProjectRequest): Unit =
    if (updates.members.exists(!_.exists(_.role == ProjectUserRole.OWNER)))
      throw new ConflictError("Requested update would remove all project owners.")
}


trait ProjectServiceProvider {
  this: JobsDaoProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  val projectService: Singleton[ProjectService] =
    Singleton(() => new ProjectService(jobsDao(), authenticator()))

  addService(projectService)
}
