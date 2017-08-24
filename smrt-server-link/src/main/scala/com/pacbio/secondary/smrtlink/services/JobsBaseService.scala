package com.pacbio.secondary.smrtlink.services

import java.io.File
import java.util.UUID
import java.nio.file.{Files, Path, Paths}

import akka.util.Timeout
import com.pacbio.secondary.smrtlink.actors.{ActorRefFactoryProvider, JobsDao, JobsDaoProvider}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.common.models.CommonModels._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModelSpraySupport
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jobtypes._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.typesafe.scalalogging.LazyLogging
import spray.http.{HttpData, HttpEntity, _}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling.Unmarshaller
import spray.httpx.marshalling.Marshaller
import spray.json._
import spray.routing._

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait JobServiceRoutes {
  def jobTypeId: JobType
  def routes: Route
}

trait CommonJobsRoutes[T <: ServiceJobOptions] extends SmrtLinkBaseMicroService with JobServiceConstants with JobServiceRoutes {
  val dao: JobsDao
  val authenticator: Authenticator

  implicit val um: Unmarshaller[T]

  //implicit val timeout = Timeout(30.seconds)

  import SmrtLinkJsonProtocols._
  import CommonModelSpraySupport._
  import CommonModelImplicits._


  // Just to get this to compile
  override val manifest = PacBioComponentManifest(
    toServiceId("status"),
    "Status Service",
    "0.2.0", "Subsystem Status Service")


  /**
    * Logging util
    *
    * @param sx String
    * @return
    */
  def andLog(sx: String): String = {
    logger.info(sx)
    sx
  }

  /**
    * Validation of the Opts returns Option[Error], This will fail
    * the Future if any validation errors occur.
    *
    * @param opts
    * @param user
    * @return
    */
  def validator(opts: T, user: Option[UserRecord]): Future[T] = {
    opts.validate() match {
      case Some(ex) => Future.failed(throw new UnprocessableEntityError(ex.msg))
      case _ => Future.successful(opts)
    }
  }

  /**
    * Central interface for creating Jobs using the ServiceJobOptions.
    *
    * This interface will handle the POST (and unmarshalling) and
    * validation and creating a new EngineJob from the ServiceJobOptions
    * provided.
    *
    * If there are customized needs, you should override this and
    * always call super.createJob.
    *
    * @param opts
    * @param user
    * @return
    */
  def createJob(opts: T, user: Option[UserRecord]): Future[EngineJob] = {
    val uuid = UUID.randomUUID()

    val name = opts.name.getOrElse(opts.jobTypeId.id)
    val comment = opts.description.getOrElse(s"Description for job ${opts.jobTypeId}")

    // Workaround to get the code to compile
    //val jsettings = opts.toJson.asJsObject
    val jsettings = "{}".parseJson.asJsObject

    val projectId = opts.projectId.getOrElse(1)

    // Mock this out. The system config will be passed into the JobService or createJob interface
    val smrtLinkVersion = Some("1.1.1")

    for {
      vopts <- validator(opts, user) // This will fail the Future if any validation errors occur.
      entryPoints <- Future(vopts.resolveEntryPoints())
      engineJob <- dao.createJob2(uuid, name, comment, vopts.jobTypeId, entryPoints, jsettings, user.map(_.userId),
        user.flatMap(_.userEmail),
        smrtLinkVersion, projectId)
    } yield engineJob
  }

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

  val routeCreateJob: Route =
    pathEndOrSingleSlash {
      post {
        optionalAuthenticate(authenticator.wso2Auth) { user =>
          entity(as[T]) { opts =>
            complete {
              created {
                createJob(opts, user)
              }
            }
          }
        }
      }
    }


  def allJobRoutes(implicit ec: ExecutionContext): Route =
    pathPrefix(IdAbleMatcher) { jobId =>
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              dao.getJobById(jobId)
            }
          }
        }
      } ~
      path(JOB_TASK_PREFIX) {
        get {
          complete {
            ok {
              dao.getJobTasks(jobId)
            }
          }
        } ~
        post {
          entity(as[CreateJobTaskRecord]) { r =>
            complete {
              created {
                dao.getJobById(jobId).flatMap { job =>
                  dao.addJobTask(JobTask(r.uuid, job.id, r.taskId, r.taskTypeId, r.name, AnalysisJobStates.CREATED.toString, r.createdAt, r.createdAt, None))
                }
              }
            }
          }
        }
      } ~
      path(JOB_TASK_PREFIX / JavaUUID) { taskUUID =>
        get {
          complete {
            ok {
              dao.getJobTask(taskUUID)
            }
          }
        } ~
        put {
          entity(as[UpdateJobTaskRecord]) { r =>
            complete {
              created {
                dao.getJobById(jobId).flatMap { engineJob =>
                 dao.updateJobTask(UpdateJobTask(engineJob.id, taskUUID, r.state, r.message, r.errorMessage))
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
                dao.getDataStoreReportByUUID(reportUUID)
              }
            }
          }
        }
      } ~
      path(JOB_REPORT_PREFIX) {
        get {
          complete {
            dao.getJobById(jobId).flatMap { engineJob =>
                dao.getDataStoreReportFilesByJobId(engineJob.id)
            }
          }
        }
      } ~
      path(JOB_EVENT_PREFIX) {
        get {
          complete {
            ok {
              dao.getJobById(jobId).flatMap { engineJob =>
                dao.getJobEventsByJobId(engineJob.id)
              }
            }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX) {
        get {
          complete {
            ok {
              dao.getJobById(jobId).flatMap { engineJob =>
                dao.getDataStoreServiceFilesByJobId(engineJob.id)
              }
            }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX / JavaUUID) { datastoreFileUUID =>
        get {
          complete {
            ok {
              dao.getDataStoreFileByUUID(datastoreFileUUID)
            }
          }
        } ~
        put {
          entity(as[DataStoreFileUpdateRequest]) { sopts =>
            complete {
              ok {
                dao.updateDataStoreFile(datastoreFileUUID, sopts.path, sopts.fileSize, sopts.isActive)
              }
            }
          }
        }
      } ~
      path(JOB_OPTIONS) {
        get {
          complete {
            ok {
              dao.getJobById(jobId).map(_.jsonSettings)
            }
          }
        }
      } ~
      path(ENTRY_POINTS_PREFIX) {
        get {
          complete {
            dao.getJobById(jobId).flatMap { engineJob =>
              dao.getJobEntryPoints(engineJob.id)
            }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX) {
        post {
          entity(as[DataStoreFile]) { dsf =>
            complete {
              created {
                dao.getJobById(jobId).flatMap { engineJob =>
                  dao.insertDataStoreFileById(dsf, engineJob.id)
                }
              }
            }
          }
        }
      } ~
      path(JOB_DATASTORE_PREFIX / JavaUUID / "download") { datastoreFileUUID =>
        get {
          complete {
            dao.getDataStoreFileByUUID(datastoreFileUUID)
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
                dao.getJobById(jobId).flatMap { engineJob =>
                  dao.insertDataStoreFileById(dsf, engineJob.id)
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
            resolveJobResource(dao.getJobById(jobId), id)
          }
        }
      } ~
      path("children") {
        complete {
          ok {
            dao.getJobChildrenByJobId(jobId).mapTo[Seq[EngineJob]]
          }
        }
      }
    }

  override def routes:Route = allJobRoutes ~ routeCreateJob
}


// All Service Jobs should be defined here. This a bit boilerplate, but it's very simple and there aren't a large number of job types
class HelloWorldJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[HelloWorldJobOptions]) extends CommonJobsRoutes[HelloWorldJobOptions] {
  override def jobTypeId = JobTypeIds.HELLO_WORLD
}

class DbBackupJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[DbBackUpJobOptions]) extends CommonJobsRoutes[DbBackUpJobOptions] {
  override def jobTypeId = JobTypeIds.DB_BACKUP
}

class DeleteDataSetJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[DeleteDataSetJobOptions]) extends CommonJobsRoutes[DeleteDataSetJobOptions] {
  override def jobTypeId = JobTypeIds.DELETE_DATASETS
}

class ImportBarcodeFastaJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[ImportBarcodeFastaJobOptions]) extends CommonJobsRoutes[ImportBarcodeFastaJobOptions] {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_BARCODES
}

class ImportDataSetJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[ImportDataSetJobOptions]) extends CommonJobsRoutes[ImportDataSetJobOptions] with DataSetFileUtils {
  //type T = ImportDataSetJobOptions
  import CommonModelImplicits._

  override def jobTypeId = JobTypeIds.IMPORT_DATASET

  private def updatePathIfNecessary(opts: ImportDataSetJobOptions): Future[EngineJob] = {
    for {
      dsMini <- Future.fromTry(Try(getDataSetMiniMeta(Paths.get(opts.path))))
      ds <- dao.getDataSetById(dsMini.uuid)
      engineJob <- dao.getJobById(ds.jobId)
      m1 <- dao.updateDataStoreFile(ds.uuid, Some(opts.path), None, true)
      m2 <- dao.updateDataSetById(ds.uuid, opts.path, true)
      _ <- Future.successful(andLog(s"$m1 $m2"))

    } yield engineJob
  }

  /**
    * Need a custom create job method to return the companion dataset job IF the
    * dataset has already been imported.
    *
    * If the path of dataset in the database is different than the provided path, the
    * path is assumed to be updated to the path provided.
    *
    * @param opts
    * @param user
    * @return
    */
  override def createJob(opts: ImportDataSetJobOptions, user: Option[UserRecord]): Future[EngineJob] = {

    val f1 = for {
      _ <- validator(opts, user)
      engineJob <- updatePathIfNecessary(opts)
    } yield engineJob

    f1.recoverWith { case err: ResourceNotFoundError => super.createJob(opts, user) }
  }

}

class ImportFastaJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[ImportFastaJobOptions]) extends CommonJobsRoutes[ImportFastaJobOptions] {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_REFERENCE

}

class MergeDataSetJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[MergeDataSetJobOptions]) extends CommonJobsRoutes[MergeDataSetJobOptions] {
  override def jobTypeId = JobTypeIds.HELLO_WORLD
}


class MockPbsmrtpipeJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[MockPbsmrtpipeJobOptions]) extends CommonJobsRoutes[MockPbsmrtpipeJobOptions] {
  override def jobTypeId = JobTypeIds.MOCK_PBSMRTPIPE
}


class PbsmrtpipeJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[PbsmrtpipeJobOptions]) extends CommonJobsRoutes[PbsmrtpipeJobOptions] {
  override def jobTypeId = JobTypeIds.PBSMRTPIPE
}


class RsConvertMovieToDataSetJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[RsConvertMovieToDataSetJobOptions]) extends CommonJobsRoutes[RsConvertMovieToDataSetJobOptions] {
  override def jobTypeId = JobTypeIds.CONVERT_RS_MOVIE
}


class SimpleJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[SimpleJobOptions]) extends CommonJobsRoutes[SimpleJobOptions] {
  override def jobTypeId = JobTypeIds.SIMPLE
}


class TsJobBundleJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[TsJobBundleJobOptions]) extends CommonJobsRoutes[TsJobBundleJobOptions] {
  override def jobTypeId = JobTypeIds.TS_JOB
}


class TsSystemStatusBundleJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[TsSystemStatusBundleJobOptions]) extends CommonJobsRoutes[TsSystemStatusBundleJobOptions] {
  override def jobTypeId = JobTypeIds.TS_SYSTEM_STATUS
}

// This is the used for the "Naked" untyped job route service <smrt-link>/<job-manager>/jobs
class NakedNoTypeJobsService(override val dao: JobsDao, override val authenticator: Authenticator)(implicit val um: Unmarshaller[SimpleJobOptions]) extends CommonJobsRoutes[SimpleJobOptions] {
  override def jobTypeId = JobTypeIds.SIMPLE
  override def createJob(opts: SimpleJobOptions, user: Option[UserRecord]): Future[EngineJob] =
    Future.failed(throw new UnprocessableEntityError("Unsupported Request for Job creation. Using specific job type endpoint"))

}

/**
  * Util to Adhere to the current SL System design
  * @param dao
  * @param authenticator
  */
class JobsServiceUtils(dao: JobsDao, authenticator: Authenticator) extends PacBioService with JobServiceConstants with LazyLogging {

  import SmrtLinkJsonProtocols._

  val manifest = PacBioComponentManifest(
    toServiceId("new_job_service"),
    "New Job Service",
    "0.1.0", "New Job Service")

  def getServiceJobs():Seq[JobServiceRoutes] = Seq(
    new DbBackupJobsService(dao, authenticator),
    new DeleteDataSetJobsService(dao, authenticator),
    new HelloWorldJobsService(dao, authenticator),
    new ImportBarcodeFastaJobsService(dao, authenticator),
    new ImportDataSetJobsService(dao, authenticator),
    new ImportFastaJobsService(dao, authenticator),
    new MergeDataSetJobsService(dao, authenticator),
    new MockPbsmrtpipeJobsService(dao, authenticator),
    new PbsmrtpipeJobsService(dao, authenticator),
    new RsConvertMovieToDataSetJobsService(dao, authenticator),
    new SimpleJobsService(dao, authenticator),
    new TsJobBundleJobsService(dao, authenticator),
    new TsSystemStatusBundleJobsService(dao, authenticator)
  )

  def getJobTypesRoute(jobTypes: Seq[JobType]): Route = {
    val jobTypeEndPoints = jobTypes.map(x => JobTypeEndPoint(x.id, x.description))

    pathPrefix(SERVICE_PREFIX / JOB_TYPES_PREFIX) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobTypeEndPoints
          }
        }
      }
    }
  }

  //def wrap(t: JobTypeService[_]): Route = pathPrefix(SERVICE_PREFIX / JOB_ROOT_PREFIX) { t.routes }
  //val jobServiceTypeRoutes = jobTypes.map(wrap).reduce(_ ~ _)

  def getServiceJobRoutes(): Route = {

    // This will NOT be wrapped in a job-type prefix
    val nakedJob = new NakedNoTypeJobsService(dao, authenticator)

    // These will be wrapped with the a job-type-id specific prefix
    val jobs = getServiceJobs()

    val jobTypeRoutes = getJobTypesRoute(jobs.map(_.jobTypeId))

    // Create all routes with <job-type-id> prefix
    val rx = jobs.map(j => pathPrefix(j.jobTypeId.id) {j.routes}).reduce(_ ~ _)

    val allJobRoutes:Route = jobTypeRoutes ~ nakedJob.allJobRoutes ~ rx


    pathPrefix("smrt-link-test") { allJobRoutes }
  }

  override def routes: Route = getServiceJobRoutes()

}

trait JobsServiceProvider {
  this: ActorRefFactoryProvider with AuthenticatorProvider with ServiceComposer with JobsDaoProvider with SmrtLinkConfigProvider =>

  val newJobService: Singleton[JobsServiceUtils] = Singleton(() => new JobsServiceUtils(jobsDao(), authenticator()))

  addService(newJobService)
}