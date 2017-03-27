
// FIXME it would be cleaner to re-use the MixedId model from DataSetService.scala

package com.pacbio.secondary.smrtlink.services

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.pacbio.common.models.CommonModels._
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.common.services.StatusCodeJoiners
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModelSpraySupport
import com.pacbio.secondary.analysis.engine.CommonMessages.{MessageResponse, ImportDataStoreFile, ImportDataStoreFileByJobId}
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{FileUtils, FilenameUtils}
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal


object JobResourceUtils extends  LazyLogging{
  // FIXME. This is a very lackluster idea.
  // This assumes an id -> file which is wrong
  def getJobResource(jobDir: String, imageFileName: String): Option[String] = {
    val jobP = Paths.get(jobDir)
    val ext = FilenameUtils.getExtension(imageFileName)
    val filterExt = if (ext.isEmpty) Seq("*") else Seq(ext)
    logger.debug(s"Trying to resolve resource '$imageFileName' with ext '$ext' from '$jobDir'")
    val it = FileUtils.iterateFiles(jobP.toFile, filterExt.toArray, true).filter(x => x.getName == imageFileName)
    it.toList.headOption match {
      case Some(x) => Some(x.toPath.toAbsolutePath.toString)
      case _ => None
    }
  }

  def resolveResourceFrom(rootJobDir: Path, imageFileName: String)(implicit ec: ExecutionContext) = Future {
    JobResourceUtils.getJobResource(rootJobDir.toAbsolutePath.toString, imageFileName).getOrElse(s"Failed to find resource '$imageFileName' from $rootJobDir")
  }
}

trait JobService
  extends LazyLogging
  with StatusCodeJoiners
  with Directives
  with JobServiceConstants {

  implicit val timeout = Timeout(30.seconds)

  import SmrtLinkJsonProtocols._
  import CommonModelSpraySupport._

  private def resolveContentType(path: Path): ContentType = {
    val mimeType = MimeTypeProperties.fromFile(path.toFile)
    val mediaType = MediaType.custom(mimeType)
    ContentType(mediaType)
  }

  private def resolveJobResource(fx: Future[EngineJob], id: String)(implicit ec: ExecutionContext): Future[HttpEntity] = {
    fx.map { engineJob =>
      JobResourceUtils.getJobResource(engineJob.path, id)
    }.recover {
      case NonFatal(e) => None
    }.flatMap {
      case Some(x) =>
        val mtype = if (id.endsWith(".png")) MediaTypes.`image/png` else MediaTypes.`text/plain`
        Future { HttpEntity(ContentType(mtype), HttpData(new File(x))) }
      case None =>
        Future.failed(throw new ResourceNotFoundError(s"Unable to find image resource '$id'"))
    }
  }

  private def toHttpEntity(path: Path): HttpEntity = {
    logger.debug(s"Resolving path ${path.toAbsolutePath.toString}")
    if (Files.exists(path)) {
      HttpEntity(resolveContentType(path), HttpData(path.toFile))
    } else {
      logger.error(s"Failed to resolve ${path.toAbsolutePath.toString}")
      throw new ResourceNotFoundError(s"Failed to find ${path.toAbsolutePath.toString}")
    }
  }

  def jobList(dbActor: ActorRef, endpoint: String, includeInactive: Boolean = false)(implicit ec: ExecutionContext): Future[Seq[EngineJob]] =
    (dbActor ? GetJobsByJobType(endpoint, includeInactive)).mapTo[Seq[EngineJob]]

  def getJobIntId(jobId: IdAble, dbActor: ActorRef)(implicit ec: ExecutionContext): Future[Int] = jobId match {
    case IntIdAble(n) => Future.successful(n)
    case UUIDIdAble(_) => (dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob].map(_.id)
  }

  def getJobUUID(jobId: IdAble, dbActor: ActorRef)(implicit ec: ExecutionContext): Future[UUID] = jobId match {
    case IntIdAble(_) => (dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob].map(_.uuid)
    case UUIDIdAble(u) => Future.successful(u)
  }

  def sharedJobRoutes(dbActor: ActorRef)(implicit ec: ExecutionContext): Route =
    pathPrefix(IdAbleMatcher) { jobId =>
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              (dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob]
            }
          }
        }
      } ~
      path(JOB_TASK_PREFIX) {
        get {
          complete {
            ok { (dbActor ? GetJobTasks(jobId)).mapTo[Seq[JobTask]]}
          }
        } ~
        post {
          entity(as[CreateJobTaskRecord]) { r =>
            complete {
              created {
                (dbActor ? CreateJobTask(r.uuid, jobId, r.taskId, r.taskTypeId, r.name, r.createdAt)).mapTo[JobTask]
              }
            }
          }
        }
      } ~
      path(JOB_TASK_PREFIX / JavaUUID) { taskUUID =>
        get {
          complete {
            ok {
              (dbActor ? GetJobTasks(jobId)).mapTo[Seq[JobTask]]
            }
          }
        } ~
        put {
          entity(as[UpdateJobTaskRecord]) { r =>
            complete {
              created {
                getJobIntId(jobId, dbActor).flatMap { intId =>
                  (dbActor ? UpdateJobTaskStatus(r.uuid, intId, r.state, r.message, r.errorMessage)).mapTo[JobTask]
                }
              }
            }
          }
        }
      } ~
      path(JOB_REPORT_PREFIX / JavaUUID) { reportUUID =>
        get {
          respondWithMediaType(MediaTypes.`application/json`) {
            complete {
              ok {
                (dbActor ? GetDataStoreReportByUUID(reportUUID)).mapTo[String]
              }
            }
          }
        }
      } ~
      path(JOB_REPORT_PREFIX) {
        get {
          complete {
            getJobIntId(jobId, dbActor).flatMap { intId =>
              (dbActor ? GetDataStoreReportFilesByJobId(intId)).mapTo[Seq[DataStoreReportFile]]
            }
          }
        }
      } ~
      path(JOB_EVENT_PREFIX) {
        get {
          complete {
            getJobIntId(jobId, dbActor).flatMap { intId =>
              (dbActor ? GetJobEventsByJobId(intId)).mapTo[Seq[JobEvent]]
            }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX) {
        get {
          complete {
            getJobIntId(jobId, dbActor).flatMap { intId =>
              (dbActor ? GetDataStoreServiceFilesByJobId(intId)).mapTo[Seq[DataStoreServiceFile]]
            }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX / JavaUUID) { datastoreFileUUID =>
        get {
          complete {
            ok {
              (dbActor ? GetDataStoreFileByUUID(datastoreFileUUID)).mapTo[DataStoreServiceFile]
            }
          }
        }
      } ~
      path(JOB_OPTIONS) {
        get {
          complete {
            ok {
              (dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob].map(_.jsonSettings)
            }
          }
        }
      } ~
      path(ENTRY_POINTS_PREFIX) {
        get {
          complete {
            getJobIntId(jobId, dbActor).flatMap { intId =>
              (dbActor ? GetEngineJobEntryPoints(intId)).mapTo[Seq[EngineJobEntryPoint]]
            }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX) {
        post {
          entity(as[DataStoreFile]) { dsf =>
            complete {
              created {
                getJobIntId(jobId, dbActor).flatMap { intId =>
                  (dbActor ? ImportDataStoreFileByJobId(dsf, intId)).mapTo[MessageResponse]
                }
              }
            }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX / JavaUUID / "download") { datastoreFileUUID =>
        get {
          complete {
            (dbActor ? GetDataStoreFileByUUID(datastoreFileUUID))
              .mapTo[DataStoreServiceFile]
              .map { dsf =>
                val httpEntity = toHttpEntity(Paths.get(dsf.path))
                // The datastore needs to be updated to be written at the root of the job dir,
                // then the paths should be relative to the root dir. This will require that the DataStoreServiceFile
                // returns the correct absolute path
                val fn = s"job-${jobId.toIdString}-${dsf.uuid.toString}-${Paths.get(dsf.path).toAbsolutePath.getFileName}"
                // Using headers= instead of respondWithHeader because need to set the name file file
                HttpResponse(entity = httpEntity, headers = List(HttpHeaders.`Content-Disposition`("attachment; filename=" + fn)))
              }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX) {
        post {
          entity(as[DataStoreFile]) { dsf =>
            complete {
              created {
                getJobUUID(jobId, dbActor).flatMap { uuid =>
                  (dbActor ? ImportDataStoreFile(dsf, uuid)).mapTo[MessageResponse]
                }
              }
            }
          }
        }
      } ~
      path("resources") {
        parameter('id) { id =>
          logger.info(s"Attempting to resolve resource $id from ${jobId.toIdString}")
          complete {
            resolveJobResource((dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob], id)
          }
        }
      } ~
      path("children") {
        complete {
          ok {
            (dbActor ? jobId.map(GetJobChildrenById, GetJobChildrenByUUID)).mapTo[Seq[EngineJob]]
          }
        }
      }
    }
}
