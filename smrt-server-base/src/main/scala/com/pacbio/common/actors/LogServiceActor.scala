package com.pacbio.common.actors

import akka.actor.{Props, ActorRef}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{LogMessageRecord, LogResourceRecord}
import org.joda.time.{DateTime => JodaDateTime}

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
 * Companion object for the LogServiceActor class, defining the set of messages it can handle.
 */
object LogServiceActor {
  case object GetAllResources
  case class CreateResource(m: LogResourceRecord)
  case class GetResource(id: String)
  case class GetMessages(id: String)
  case class CreateMessage(id: String, m: LogMessageRecord)
  case object GetSystemMessages

  case class SearchMessages(id: String, criteria: SearchCriteria)
  case class SearchSystemMessages(criteria: SearchCriteria)
}

/**
 * Akka actor that wraps a LogDao.
 */
class LogServiceActor(logDao: LogDao) extends PacBioActor {

  import LogServiceActor._

  def receive: Receive = {
    case GetAllResources                                      => respondWith(logDao.getAllLogResources)
    case CreateResource(m: LogResourceRecord)                 => respondWith(logDao.createLogResource(m))
    case GetResource(id: String)                              => respondWith(logDao.getLogResource(id))
    case GetMessages(id: String)                              => respondWith(logDao.getLogMessages(id))
    case CreateMessage(id: String, m: LogMessageRecord)       => respondWith(logDao.createLogMessage(id, m))
    case SearchMessages(id: String, criteria: SearchCriteria) => pipeWith(logDao.searchLogMessages(id, criteria))
    case GetSystemMessages                                    => respondWith(logDao.getSystemLogMessages)
    case SearchSystemMessages(criteria: SearchCriteria)       => pipeWith(logDao.searchSystemLogMessages(criteria))
  }

  override def postStop(): Unit = logDao.flushAll()
}

/**
 * Provides a singleton ActorRef for a LogServiceActor. Concrete providers must mixin a LogDaoProvider and an
 * ActorRefFactoryProvider.
 */
trait LogServiceActorRefProvider {
  this: LogDaoProvider with ActorRefFactoryProvider =>

  val logServiceActorRef: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(Props(classOf[LogServiceActor], logDao())))
}

/**
 * Provides a singleton LogServiceActor. Concrete providers must mixin a LogDaoProvider. Note that this provider is
 * designed for tests, and should generally not be used in production. To create a production app, use the
 * {{{LogServiceActorRefProvider}}}.
 */
trait LogServiceActorProvider {
  this: LogDaoProvider =>

  val logServiceActor: Singleton[LogServiceActor] = Singleton(() => new LogServiceActor(logDao()))
}
