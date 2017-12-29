package com.pacbio.secondary.smrtlink.services

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  ConflictError,
  ResourceNotFoundError
}
import com.pacbio.secondary.smrtlink.services.utils.SmrtDirectives
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.actors.{JobsDao, JobsDaoProvider}
import com.pacbio.secondary.smrtlink.models._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive, Directive0}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Accessing Projects
  */
class ProjectService(jobsDao: JobsDao)
    extends SmrtLinkBaseRouteMicroService
    with SmrtLinkConstants {

  // import serialzation protocols
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val manifest = PacBioComponentManifest(toServiceId("smrtlink.project"),
                                         "SMRT Link Project Service",
                                         "0.1.0",
                                         "Project create/read/update")

  def fullProject(proj: Project): Future[FullProject] =
    for {
      datasets <- jobsDao.getDatasetsByProject(proj.id)
      dbUsers <- jobsDao.getProjectUsers(proj.id)
      userResponses <- Future(
        dbUsers.map(u => ProjectRequestUser(u.login, u.role)))
    } yield proj.makeFull(datasets, userResponses)

  def maybeFullProject(projId: Int): Option[Project] => Future[FullProject] = {
    {
      case Some(proj) => fullProject(proj)
      case None =>
        throw ResourceNotFoundError(s"Unable to find project $projId")
    }
  }

  def translateConflict(sopts: ProjectRequest)
    : PartialFunction[Throwable, Future[FullProject]] = {
    {
      case ex: Throwable if jobsDao.isConstraintViolation(ex) =>
        Future.failed(
          ConflictError(s"There is already a project named ${sopts.name}"))
    }
  }

  private val readRoles: Set[ProjectUserRole.ProjectUserRole] =
    Set(ProjectUserRole.OWNER,
        ProjectUserRole.CAN_EDIT,
        ProjectUserRole.CAN_VIEW)

  private val writeRoles: Set[ProjectUserRole.ProjectUserRole] =
    Set(ProjectUserRole.OWNER, ProjectUserRole.CAN_EDIT)

  private val ownerRole: Set[ProjectUserRole.ProjectUserRole] =
    Set(ProjectUserRole.OWNER)

  private def userCanRead(login: String, projectId: Int): Future[Boolean] =
    jobsDao.userHasProjectRole(login, projectId, readRoles)

  private def userCanWrite(login: String, projectId: Int): Future[Boolean] =
    jobsDao.userHasProjectRole(login, projectId, writeRoles)

  private def userIsOwner(login: String, projectId: Int): Future[Boolean] =
    jobsDao.userHasProjectRole(login, projectId, ownerRole)

  def validateProjectUpdates(updates: ProjectRequest): Future[ProjectRequest] = {
    if (updates.members.exists(!_.exists(_.role == ProjectUserRole.OWNER))) {
      Future.failed(
        ConflictError("Requested update would remove all project owners."))
    } else {
      Future.successful(updates)
    }
  }

  val routes =
    pathPrefix("projects") {
      pathEndOrSingleSlash {
        post {
          SmrtDirectives.extractRequiredUserRecord { user =>
            entity(as[ProjectRequest]) { sopts =>
              complete {
                StatusCodes.Created -> {
                  validateProjectUpdates(sopts).flatMap { validProjectUpdate =>
                    jobsDao
                      .createProject(validProjectUpdate)
                      .flatMap(fullProject)
                      .recoverWith(translateConflict(sopts))
                  }
                }
              }
            }
          }
        } ~
          get {
            SmrtDirectives.extractRequiredUserRecord { user =>
              complete {
                jobsDao.getUserProjects(user.userId).map(_.map(_.project))
              }
            }
          }
      } ~
        pathPrefix(IntNumber) { projId =>
          pathEndOrSingleSlash {
            put {
              SmrtDirectives.extractRequiredUserRecord { user =>
                entity(as[ProjectRequest]) { sopts =>
                  authorizeAsync(_ => userCanWrite(user.userId, projId)) {
                    complete {
                      validateProjectUpdates(sopts).flatMap {
                        validProjectUpdate =>
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
                SmrtDirectives.extractRequiredUserRecord { user =>
                  authorizeAsync(_ => userCanRead(user.userId, projId)) {
                    complete {
                      jobsDao
                        .getProjectById(projId)
                        .flatMap(maybeFullProject(projId))
                    }
                  }
                }
              } ~
              delete {
                SmrtDirectives.extractRequiredUserRecord { user =>
                  authorizeAsync(_ => userIsOwner(user.userId, projId)) {
                    complete {
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
          SmrtDirectives.extractRequiredUserRecord { user =>
            complete {
              jobsDao.getUserProjectsDatasets(login)
            }
          }
        }
      } ~
      path("user-projects" / Segment) { login =>
        get {
          SmrtDirectives.extractRequiredUserRecord { user =>
            complete {
              jobsDao.getUserProjects(login)
            }
          }
        }
      }
}

trait ProjectServiceProvider { this: JobsDaoProvider with ServiceComposer =>

  val projectService: Singleton[ProjectService] =
    Singleton(() => new ProjectService(jobsDao()))

  addService(projectService)
}
