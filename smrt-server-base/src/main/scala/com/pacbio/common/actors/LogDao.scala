package com.pacbio.common.actors

import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors
import com.pacbio.common.time.{Clock, ClockProvider}
import org.joda.time.{DateTime => JodaDateTime}

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
  def createLogResource(m: LogResourceRecord): Future[String]

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
}

/**
 * Provider for injecting a singleton LogDao. Concrete providers must override the logDao val, and may optionally
 * override the logDaoResponseSize val (default = 1000).
 */
trait LogDaoProvider {

  /**
   * Logging DAO object.
   */
  val logDao: Singleton[LogDao]

  /**
   * Defines the maximum number of messages per response. This is the maximum number of messages that will be returned
   * by the getLogMessages, getSystemLogMessages, searchLogMessages, and searchSystemLogMessages methods.
   *
   * <p> Default = 1000
   */
  val logDaoResponseSize: Int = LogDaoConstants.DEFAULT_RESPONSE_SIZE
}

/**
 * Object containing constants used by the logging system.
 */
object LogDaoConstants {
  val SYSTEM_ID = "system.log"
  val DEFAULT_RESPONSE_SIZE = 1000
  val SYSTEM_RESOURCE = LogResourceRecord(
    "Log resource for the entire system",
    LogDaoConstants.SYSTEM_ID,
    "System Log")
}

class InMemoryLogDao(clock: Clock, responseSize: Int) extends LogDao {
  import PacBioServiceErrors._

  private val systemResource = LogResource(
    clock.dateNow(),
    LogDaoConstants.SYSTEM_RESOURCE.description,
    LogDaoConstants.SYSTEM_ID,
    LogDaoConstants.SYSTEM_RESOURCE.name)

  private val systemBuffer = new mutable.Queue[LogMessage]

  private val resources = new mutable.HashMap[String, LogResource]
  private val buffers = new mutable.HashMap[String, mutable.Queue[LogMessage]]

  private def search(buffer: mutable.Queue[LogMessage], criteria: Option[SearchCriteria] = None): Seq[LogMessage] = {
    buffer.synchronized {
      var results: Seq[LogMessage] = buffer.toSeq
      criteria.foreach { c =>
        c.substring.foreach { s => results = results.filter(_.message.contains(s)) }
        c.sourceId.foreach { s => results = results.filter(_.sourceId == s) }
        c.startTime.foreach { t => results = results.filter { m =>
          m.createdAt.isAfter(t) || m.createdAt.isEqual(t)
        }}
        c.endTime.foreach { t => results = results.filter(_.createdAt.isBefore(t)) }
      }
      results
    }
  }

  override def getAllLogResources: Future[Seq[LogResource]] = Future(resources.values.toSeq)

  override def createLogResource(m: LogResourceRecord): Future[String] = Future {
    val id = m.id
    resources.synchronized {
      if (resources contains id)
        throw new UnprocessableEntityError(s"Resource with id $id already exists")
      else if (id == LogDaoConstants.SYSTEM_ID)
        throw new UnprocessableEntityError(s"Resource with id $id is reserved for the system log")
      else {
        val newResource = LogResource(clock.dateNow(), m.description, m.id, m.name)
        resources(id) = newResource
        buffers(id) = new mutable.Queue
        s"Successfully created resource $id"
      }
    }
  }

  override def getLogResource(id: String): Future[LogResource] = Future {
    if (resources contains id)
      resources(id)
    else if (id == LogDaoConstants.SYSTEM_ID)
      systemResource
    else throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override def getLogMessages(id: String): Future[Seq[LogMessage]] = Future {
    if (buffers contains id) search(buffers(id)) else throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override def createLogMessage(id: String, m: LogMessageRecord): Future[LogMessage] = Future {
    if (resources contains id) {
      buffers(id).synchronized {
        systemBuffer.synchronized {
          val newMessage = LogMessage(clock.dateNow(), UUID.randomUUID(), m.message, m.level, m.sourceId)
          buffers(id).enqueue(newMessage)
          systemBuffer.enqueue(newMessage)
          while (buffers(id).size > responseSize) buffers(id).dequeue()
          while (systemBuffer.size > responseSize) systemBuffer.dequeue()
          newMessage
        }
      }
    } else throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override def getSystemLogMessages: Future[Seq[LogMessage]] = Future(search(systemBuffer))

  override def searchLogMessages(id: String, criteria: SearchCriteria): Future[Seq[LogMessage]] = Future {
    if (buffers contains id) {
      search(buffers(id), Some(criteria))
    } else throw new ResourceNotFoundError(s"Unable to find resource $id")
  }

  override def searchSystemLogMessages(criteria: SearchCriteria): Future[Seq[LogMessage]] =
    Future(search(systemBuffer, Some(criteria)))

  @VisibleForTesting
  def clear(): Unit = {
    systemBuffer.synchronized {
      resources.synchronized {
        buffers.synchronized {
          systemBuffer.clear()
          resources.clear()
          buffers.clear()
        }
      }
    }
  }
}

/**
 * Provides an InMemoryLogDao.
 */
trait InMemoryLogDaoProvider extends LogDaoProvider {
  this: ClockProvider =>

  override final val logDao: Singleton[LogDao] =
    Singleton(() => new InMemoryLogDao(clock(), logDaoResponseSize))
}
