package com.pacbio.common.actors

import java.io.{BufferedWriter, File, FileWriter, PrintWriter}
import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{BaseJsonProtocol, _}
import com.pacbio.common.services.PacBioServiceErrors
import com.pacbio.common.time.{Clock, ClockProvider}
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/**
 * Represents a set of search criteria for searching log messages.
 * @param substring if present, only log messages containing this substring will be returned
 * @param sourceId if present, only log messages from the specified source will be returned
 * @param startTime if present, only log messages from this time or after will be returned (as ms since epoch)
 * @param endTime if present, only log messages from before this time will be returned (as ms since epoch)
 */
case class SearchCriteria(substring: Option[String],
                          sourceId: Option[String],
                          startTime: Option[JodaDateTime],
                          endTime: Option[JodaDateTime])

/**
 * Interface for the Logging DAO
 */
trait LogDao {
  /**
   * Provides a list of all log resources.
   */
  def getAllLogResources: Future[Seq[LogResource]]

  /**
   * Creates a new log resource.
   */
  def createLogResource(m: LogResourceRecord): Future[MessageResponse]

  /**
   * Gets a specific log resource by id.
   */
  def getLogResource(id: String): Future[LogResource]

  /**
   * Gets recent messages from a given log resource.
   */
  def getLogMessages(id: String): Future[Seq[LogMessage]]

  /**
   * Creates a new log message.
   */
  def createLogMessage(id: String, m: LogMessageRecord): Future[LogMessage]

  /**
   * Gets recent messages from all log resources.
   */
  def getSystemLogMessages: Future[Seq[LogMessage]]

  /**
   * Searches for messages in a given log resource that contain the given substring.
   */
  def searchLogMessages(id: String, criteria: SearchCriteria): Future[Seq[LogMessage]]

  /**
   * Searches for messages from all log resources that contain the given substring.
   */
  def searchSystemLogMessages(criteria: SearchCriteria): Future[Seq[LogMessage]]

  /**
   * Flush all messages from buffers into long-term storage.
   */
  def flushAll(): Future[Unit]
}

/**
 * Provider for injecting a singleton LogDao. Concrete providers must override the logDao val, and may optionally
 * override the logDaoBufferSize val (default = 1000).
 */
trait LogDaoProvider {

  /**
   * Logging DAO object.
   */
  val logDao: Singleton[LogDao]

  /**
   * Defines the maximum number of messages to be buffered. This is the maximum number of messages that will be
   * returned by the getLogMessages, getSystemLogMessages, searchLogMessages, and searchSystemLogMessages methods.
   * Once a buffer reaches the maximum size, every new message will cause the most stale message to be removed from
   * the buffer, and handled by handleStaleMessage.
   *
   * <p> Default = 1000
   */
  val logDaoBufferSize: Int = LogDaoConstants.DEFAULT_BUFFER_SIZE
}

/**
 * Object containing constants used by the logging system.
 */
object LogDaoConstants {
  val SYSTEM_ID = "system.log"
  val DEFAULT_BUFFER_SIZE = 1000
}

/**
 * Abstract implementation of LogDao that manages resources and buffers messages. Subclasses only need to handle the
 * treatment of stale messages. This is done by defining an implementation of the LogBuffer trait and providing these
 * buffers via the newBuffer method. (See InMemoryLogDaoComponent and FileLogDaoComponent below for examples of how to
 * extend this class.)
 */
abstract class AbstractLogDao(clock: Clock, bufferSize: Int) extends LogDao {
  import PacBioServiceErrors._

  private val systemResource = LogResource(
    clock.dateNow(),
    "Log resource for the entire system",
    LogDaoConstants.SYSTEM_ID,
    "System Log")
  private lazy val systemBuffer = newBuffer(LogDaoConstants.SYSTEM_ID)

  private val resources = new mutable.HashMap[String, LogResource]
  private val buffers = new mutable.HashMap[String, LogBuffer]

  /**
   * A buffer to store recent log messages. Nothing is required to implement this trait, but implementations can
   * override the handleStaleMessage method, which does nothing by default, meaning that stale messages are deleted.
   */
  protected trait LogBuffer {
    private val buffer = new mutable.Queue[LogMessage]

    final def getRecent = buffer.toSeq

    final def +=(message: LogMessage) = buffer(message)

    final def buffer(message: LogMessage): Future[Unit] = {
      buffer.enqueue(message)
      handleUntilSizeIs(bufferSize)
    }

    final def flush(): Future[Unit] = handleUntilSizeIs(0)

    private def handleUntilSizeIs(size: Int): Future[Unit] = {
      var f: Future[Unit] = Future.successful(())
      while (buffer.length > size){
        val s = buffer.dequeue()
        f = f.flatMap(_ => handleStaleMessage(s))
      }
      f
    }

    @VisibleForTesting
    final def clear(): Unit = buffer.clear()

    final def searchMessages(criteria: SearchCriteria): Future[Seq[LogMessage]] = {
      var bufferedResults = buffer
      if (criteria.substring.isDefined)
        bufferedResults = bufferedResults.filter(_.message.contains(criteria.substring.get))
      if (criteria.sourceId.isDefined)
        bufferedResults = bufferedResults.filter(_.sourceId == criteria.sourceId.get)
      if (criteria.startTime.isDefined)
        bufferedResults = bufferedResults.filter(m =>
          m.createdAt.isAfter(criteria.startTime.get) || m.createdAt.isEqual(criteria.startTime.get))
      if (criteria.endTime.isDefined)
        bufferedResults = bufferedResults.filter(_.createdAt.isBefore(criteria.endTime.get))

      val staleResults = searchStaleMessages(criteria, bufferSize - bufferedResults.size)
      staleResults.map(_ ++ bufferedResults)
    }

    /**
     * Handles stale messages. Once the buffer reaches its maximum size, stale messages will be removed from the buffer
     * and processed by this method.
     *
     * <p> Does nothing by default, essentially causing stale messages to be deleted. Subclasses may override this
     * method to provide for custom storage or handling of stale messages.
     */
    protected def handleStaleMessage(message: LogMessage): Future[Unit] = Future.successful(())

    /**
     * Searches messages that have been flushed from the buffer via handleStaleMessage. The returned list should be in
     * ascending order of creation time, and should contain no more than the number of messages provided by the limit
     * parameter.
     *
     * <p> By default, this returns Nil. Subclasses may override this method to search their custom storage solutions
     * for matching messages.
     */
    protected def searchStaleMessages(criteria: SearchCriteria, limit: Int): Future[Seq[LogMessage]] =
      Future.successful(Nil)
  }

  /**
   * Creates a new buffer for log messages sent to the resource with the given id. This will also be called once at
   * start-up to create a buffer for system messages.
   */
  def newBuffer(id: String): LogBuffer

  override final def getAllLogResources: Future[Seq[LogResource]] = Future(resources.values.toSeq)

  override final def createLogResource(m: LogResourceRecord): Future[MessageResponse] = Future {
    resources.synchronized {
      val id = m.id
      if (resources contains id)
        throw new UnprocessableEntityError(s"Resource with id $id already exists")
      else if (id == LogDaoConstants.SYSTEM_ID)
        throw new UnprocessableEntityError(s"Resource with id $id is reserved for the system log")
      else {
        val newResource = LogResource(clock.dateNow(), m.description, m.id, m.name)
        resources(id) = newResource
        buffers(id) = newBuffer(id)
        MessageResponse(s"Successfully created resource $id")
      }
    }
  }

  override final def getLogResource(id: String): Future[LogResource] = Future {
    if (resources contains id)
      resources(id)
    else if (id == LogDaoConstants.SYSTEM_ID)
      systemResource
    else throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override final def getLogMessages(id: String): Future[Seq[LogMessage]] =
    Future(if (buffers contains id) buffers.get(id).get.getRecent else Nil)

  override final def createLogMessage(id: String, m: LogMessageRecord): Future[LogMessage] =
    Future(resources contains id).flatMap {
      case true =>
        systemBuffer.synchronized {
          val newMessage = LogMessage(clock.dateNow(), UUID.randomUUID(), m.message, m.level, m.sourceId)
          Future.sequence(Seq(buffers(id) += newMessage, systemBuffer += newMessage)).map(_ => newMessage)
        }
      case _ => throw new ResourceNotFoundError(s"Unable to find resource $id")
    }

  override final def getSystemLogMessages: Future[Seq[LogMessage]] = Future(systemBuffer.getRecent)

  override final def searchLogMessages(id: String, criteria: SearchCriteria): Future[Seq[LogMessage]] =
    if (resources contains id) {
      buffers(id).searchMessages(criteria)
    } else Future(Nil)

  override final def searchSystemLogMessages(criteria: SearchCriteria): Future[Seq[LogMessage]] =
    systemBuffer.searchMessages(criteria)

  override def flushAll(): Future[Unit] = systemBuffer.synchronized {
    Future.sequence((buffers.values.toSeq :+ systemBuffer).map(_.flush())).map(_ => ())
  }

  @VisibleForTesting
  def clear(): Future[Unit] = Future {
    systemBuffer.synchronized {
      systemBuffer.clear()
    }
    resources.synchronized {
      resources.clear()
      buffers.clear()
    }
  }
}

/**
 * Concrete implementation of LogDao that stores log messages in memory and deletes stale messages once its buffers
 * become full.
 */
class InMemoryLogDao(clock: Clock, bufferSize: Int) extends AbstractLogDao(clock, bufferSize) {
  override def newBuffer(id: String) = new LogBuffer {}
}

/**
 * Provides an InMemoryLogDao.
 */
trait InMemoryLogDaoProvider extends LogDaoProvider {
  this: ClockProvider =>

  override final val logDao: Singleton[LogDao] = Singleton(() => new InMemoryLogDao(clock(), logDaoBufferSize))
}

/**
 * Concrete implementation of LogDao that stores recent log messages in memory and writes stale log messages to files
 * after they're removed from the in-memory buffers. The file names are equal to the log resource ids.
 */
class FileLogDao(logDirPath: String, clock: Clock, bufferSize: Int) extends AbstractLogDao(clock, bufferSize) {
  override def newBuffer(id: String) = new LogBuffer with BaseJsonProtocol {
    // TODO(smcclellan): Consider sharding log files by date.
    val writer = new PrintWriter(new BufferedWriter(new FileWriter(new File(logDirPath, id))))

    override def handleStaleMessage(message: LogMessage): Future[Unit] =
      Future(writer.println(message.toJson.compactPrint))
  }
}

/**
 * Provides a FileLogDao. Concrete providers must override the logDaoDirPath val.
 */
trait FileLogDaoProvider extends LogDaoProvider {
  this: ClockProvider =>

  val logDaoDirPath: String

  override final val logDao: Singleton[LogDao] =
    Singleton(() => new FileLogDao(logDaoDirPath, clock(), logDaoBufferSize))
}
