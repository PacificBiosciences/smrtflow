package com.pacbio.common.actors

import java.io.File
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._
import java.util.UUID

import akka.actor.{Cancellable, ActorSystem}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LoggerFactoryProvider, Logger, LoggerFactory, LogResources}
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors
import com.pacbio.common.time.{PacBioDateTimeFormat, ClockProvider, Clock}
import org.joda.time.{DateTime => JodaDateTime, Duration => JodaDuration}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

// TODO(smcclellan): add scaladoc, unittests

object CleanupDao {
  val LOG_RESOURCE_ID = "cleanup-service"
  val LOG_SOURCE_ID_PREFIX = "cleanup-job-"
  def getSourceId(jobId: UUID): String = LOG_SOURCE_ID_PREFIX + jobId.toString
  def getConfigSourceId(name: String): String = name
}

trait CleanupDao {
  def getAllJobs: Future[Set[CleanupJobResponse]]
  def getJob(id: String): Future[CleanupJobResponse]
  def createJob(create: ApiCleanupJobCreate): Future[CleanupJobResponse]
  def startJob(id: String): Future[CleanupJobResponse]
  def pauseJob(id: String): Future[CleanupJobResponse]
  def deleteJob(id: String): Future[String]

  def runApiJob(uuid: UUID): Future[Unit]

  def createConfigJob(create: ConfigCleanupJobCreate): Future[CleanupJobResponse]
  def runConfigJob(name: String): Future[Unit]
}

trait CleanupDaoProvider {
  import CleanupDao._

  val cleanupDao: Singleton[CleanupDao]

  val cleanupServiceLogResource: Singleton[LogResourceRecord] =
    Singleton(LogResourceRecord("Cleanup Service Loggger", LOG_RESOURCE_ID, "Cleanup Service")).bindToSet(LogResources)
}

abstract class AbstractCleanupDao(clock: Clock, system: ActorSystem, loggerFactory: LoggerFactory) extends CleanupDao {
  import CleanupDao._
  import PacBioServiceErrors._

  val apiJobs: mutable.Map[UUID, ApiCleanupJob] = new mutable.HashMap
  val apiLoggers: mutable.Map[UUID, Logger] = new mutable.HashMap
  val apiCancels: mutable.Map[UUID, Cancellable] = new mutable.HashMap

  val configJobs: mutable.Map[String, ConfigCleanupJob] = new mutable.HashMap
  val configLoggers: mutable.Map[String, Logger] = new mutable.HashMap

  private def allJobs: Map[String, CleanupJobBase[_]] = (apiJobs.map(e => e._1.toString -> e._2) ++ configJobs).toMap

  // TODO(smcclellan): Remove this?
  def loadJobs(): Unit // Abstract method to load jobs from storage into memory on initialization

  override def getAllJobs: Future[Set[CleanupJobResponse]] = Future(allJobs.values.map(_.toResponse).toSet)

  override def getJob(id: String): Future[CleanupJobResponse] = Future {
    val jobs = allJobs
    if (jobs.contains(id))
      jobs(id).toResponse
    else
      throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override def createJob(create: ApiCleanupJobCreate): Future[CleanupJobResponse] = Future {
    if (create.at < 0 || create.at >= create.frequency.getTickRange)
      throw new UnprocessableEntityError(s"Value of at (${create.at}) not in allowable range" +
        s"(0-${create.frequency.getTickRange - 1}) for frequency ${create.frequency.toString}.")
    else if (!new File(create.target).isAbsolute)
      throw new UnprocessableEntityError("Relative path names are not supported.")
    else {
      val start = create.start.getOrElse(false) // Do not start jobs by default
      val dryRun = create.dryRun.getOrElse(false) // Assume real runs

      val job = ApiCleanupJob(
        UUID.randomUUID(),
        create.target,
        create.frequency,
        create.at,
        create.olderThan,
        create.minSize,
        start,
        dryRun,
        lastCheck = None,
        lastDelete = None)
      apiJobs += job.uuid -> job
      apiLoggers += job.uuid -> loggerFactory.getLogger(LOG_RESOURCE_ID, getSourceId(job.uuid))

      if (start) schedule(job.uuid)

      job.toResponse
    }
  }

  override def startJob(id: String): Future[CleanupJobResponse] = Future {
    val jobs = allJobs
    if (!jobs.contains(id))
      throw new ResourceNotFoundError(s"Unable to find resource $id")
    else if (!jobs(id).isInstanceOf[ApiCleanupJob])
      throw new MethodNotImplementedError("Cleanup jobs not created through the API cannot be started or paused")

    val job = jobs(id).asInstanceOf[ApiCleanupJob]
    val uuid = job.uuid
    if (job.running)
      job.toResponse
    else {
      schedule(uuid)
      apiJobs(uuid) = job.copy(running = true)
      apiJobs(uuid).toResponse
    }
  }

  override def pauseJob(id: String): Future[CleanupJobResponse] = Future {
    val jobs = allJobs
    if (!jobs.contains(id))
      throw new ResourceNotFoundError(s"Unable to find resource $id")
    else if (!jobs(id).isInstanceOf[ApiCleanupJob])
      throw new MethodNotImplementedError("Cleanup jobs not created through the API cannot be started or paused")

    val job = jobs(id).asInstanceOf[ApiCleanupJob]
    val uuid = job.uuid
    if (!job.running)
      job.toResponse
    else {
      if (cancel(uuid)) {
        apiJobs(uuid) = job.copy(running = false)
        apiJobs(uuid).toResponse
      } else
        throw new RuntimeException(s"Unable to pause job $uuid")
    }
  }

  override def deleteJob(id: String): Future[String] = Future {
    val jobs = allJobs
    if (!jobs.contains(id))
      throw new ResourceNotFoundError(s"Unable to find resource $id")
    else if (!jobs(id).isInstanceOf[ApiCleanupJob])
      throw new MethodNotImplementedError("Cleanup jobs not created through the API cannot be deleted")

    pauseJob(id)
    apiJobs -= UUID.fromString(id)
    s"Successfully deleted job $id."
  }

  override def runApiJob(uuid: UUID): Future[Unit] = Future {
    if (apiJobs.contains(uuid)) {
      val logger = apiLoggers(uuid)
      logger.info(s"Running cleanup job $uuid")
      apiJobs(uuid) = apiJobs(uuid).checked(clock.dateNow())
      runJob(logger, apiJobs(uuid))
    }
  }

  override def createConfigJob(create: ConfigCleanupJobCreate): Future[CleanupJobResponse] = Future {
    if (!new File(create.target).isAbsolute)
      throw new UnprocessableEntityError("Relative path names are not supported.")
    else {
      val dryRun = create.dryRun.getOrElse(false) // Assume real runs

      val job = ConfigCleanupJob(
        create.name,
        create.target,
        create.schedule,
        create.olderThan,
        create.minSize, dryRun,
        lastCheck = None,
        lastDelete = None)
      configJobs += job.name -> job
      configLoggers += job.name -> loggerFactory.getLogger(LOG_RESOURCE_ID, getConfigSourceId(job.name))

      job.toResponse
    }
  }

  override def runConfigJob(name: String): Future[Unit] = Future {
    if (configJobs.contains(name)) {
      val logger = configLoggers(name)
      logger.info(s"Running configured cleanup job $name")
      configJobs(name) = configJobs(name).checked(clock.dateNow())
      runJob(logger, configJobs(name))
    }
  }

  private def runJob[T <: CleanupJobBase[T]](logger: Logger, job: T): T = {
    import PacBioDateTimeFormat.{DATE_TIME_FORMAT, TIME_ZONE}

    def delete(file: File): Boolean = {
      val fileOrDir = if (file.isDirectory) "directory" else "file"
      if (job.dryRun) {
        // The question of whether a file can be deleted is system-dependent, and java.io's File api does not offer a
        // canDelete method, so this is a best guess.
        val canDelete = file.getParentFile.canExecute && file.getParentFile.canWrite
        if (canDelete)
          logger.info(s"Dry run cleanup job ${job.id} would have deleted $fileOrDir: ${file.getAbsolutePath}")
        else
          logger.error(s"Dry run cleanup job ${job.id} would have FAILED to delete $fileOrDir: ${file.getAbsolutePath}")
        canDelete
      } else {
        val deleted = file.delete
        if (deleted)
          logger.info(s"Cleanup job ${job.id} deleted $fileOrDir: ${file.getAbsolutePath}")
        else
          logger.error(s"Cleanup job ${job.id} FAILED to delete $fileOrDir: ${file.getAbsolutePath}")
        deleted
      }
    }

    // TODO(smcclellan): Provide FileSystem via dependency injection (for testing)
    val pathMatcher = FileSystems.getDefault.getPathMatcher(s"glob:${job.target}")
    var startingPath = Paths.get(job.target)
    while (!startingPath.toFile.exists()) { startingPath = startingPath.getParent }

    val foundFiles: mutable.Set[Path] = new mutable.HashSet
    val foundDirs: mutable.Set[Path] = new mutable.HashSet

    object Finder extends SimpleFileVisitor[Path] {
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (pathMatcher.matches(dir)) foundDirs += dir
        FileVisitResult.CONTINUE
      }

      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (pathMatcher.matches(file)) foundFiles += file
        FileVisitResult.CONTINUE
      }
    }

    Files.walkFileTree(startingPath, Finder)

    var totalSize = 0L
    foundFiles.map(_.toFile).foreach { file =>
      val size = file.length
      val modified = new JodaDateTime(file.lastModified(), TIME_ZONE)
      val modifiedStr = DATE_TIME_FORMAT.print(modified)

      logger.info(s"Cleanup job ${job.id} found target ${file.getAbsolutePath}. Size = $size B. Modified = $modifiedStr.")
      if (!file.canRead)
        logger.warn(s"Cleanup job ${job.id} can not read target ${file.getAbsolutePath}")
      if (!file.canWrite)
        logger.warn(s"Cleanup job ${job.id} can not write target ${file.getAbsolutePath}")

      totalSize += size
    }

    if (job.minSize.isDefined) {
      if (totalSize < job.minSize.get.bytes) {
        logger.info(s"Cleanup job ${job.id} exited due to minSize restriction: $totalSize B < ${job.minSize.get.toString}")
        return job
      }
    }

    val deleteTime = clock.dateNow()

    var deleted = false
    foundFiles.map(_.toFile).foreach { file =>
      if (job.olderThan.isDefined) {
        val modified = new JodaDateTime(file.lastModified(), TIME_ZONE)
        val modifiedStr = DATE_TIME_FORMAT.print(modified)

        val limit = job.olderThan.get.toMillis
        val sinceModified = new JodaDuration(modified, deleteTime).getMillis

        if (sinceModified > limit)
          deleted = delete(file) || deleted
        else
          logger.info(s"Cleanup job ${job.id} skipped file ${file.getAbsolutePath} due to recency: $modifiedStr < ${job.olderThan.get.toString} ago")
      } else {
        deleted = delete(file) || deleted
      }
    }

    foundDirs.map(_.toFile).foreach { file =>
      if (file.listFiles().isEmpty) deleted = delete(file) || deleted
    }

    if (deleted) job.deleted(deleteTime) else job
  }

  private def schedule(uuid: UUID): Unit = {
    val job = apiJobs(uuid)
    val freq = job.frequency
    val wait = freq.waitTime(clock.dateNow(), job.at)
    apiCancels(job.uuid) = system.scheduler.schedule(wait, freq.getPeriod, new Runnable {
      override def run(): Unit = runApiJob(uuid)
    })
    apiLoggers(job.uuid).info(s"Scheduled cleanup job $uuid.")
  }

  private def cancel(uuid: UUID): Boolean =
    if (apiCancels(uuid).cancel()) {
      apiCancels -= uuid
      apiLoggers(uuid).info(s"Canceled cleanup job $uuid.")
      true
    } else {
      apiLoggers(uuid).error(s"Failed to cancel cleanup job $uuid!")
      false
    }
}

class InMemoryCleanupDao(clock: Clock, system: ActorSystem, loggerFactory: LoggerFactory)
  extends AbstractCleanupDao(clock, system, loggerFactory) {

  override def loadJobs() = ()
}

trait InMemoryCleanupDaoProvider extends CleanupDaoProvider {
  this: ClockProvider with ActorSystemProvider with LoggerFactoryProvider =>

  override val cleanupDao: Singleton[CleanupDao] =
    Singleton(() => new InMemoryCleanupDao(clock(), actorSystem(), loggerFactory()))
}