package com.pacbio.common.actors

import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors
import com.pacbio.common.time.{ClockProvider, Clock}

import scala.collection.mutable
import scala.concurrent.Future

/**
 * Interface for the Health service DAO.
 */
trait HealthDao {
  /**
   * Provides a list of all health gauges.
   */
  def getAllHealthGauges: Seq[HealthGauge]

  /**
   * Gets a specific health gauge by id.
   */
  def getHealthGauge(id: String): HealthGauge

  /**
   * Create a new health gauge.
   */
  def createHealthGauge(m: HealthGaugeRecord): String

  /**
   * Gets the current health state of every gauge.
   */
  def getAllHealthMessages(id: String): Future[Seq[HealthGaugeMessage]]

  /**
   * Updates a health gauge with a new message.
   */
  def createHealthMessage(id: String, m: HealthGaugeMessageRecord): HealthGaugeMessage

  /**
   * Gets a list of the gauges that have the highest severity.
   */
  def getSevereHealthGauges: Seq[HealthGauge]
}

/**
 * Provider for injecting a singleton HealthDao. Concrete providers must override the healthDao val.
 */
trait HealthDaoProvider {
  /**
   * Singleton Logging DAO object.
   */
  val healthDao: Singleton[HealthDao]
}

/**
 * Abstract implementation of HealthDao that manages gauges. Subclasses only need to handle messages. This is
 * done by defining an implementation of the HealthMessageHandler trait and providing these handlers via the newHandler
 * method. (See InMemoryHealthDaoComponent below for an example of how to do this.)
 */
abstract class AbstractHealthDao(clock: Clock) extends HealthDao {
  import PacBioServiceErrors._

  val gauges = new mutable.HashMap[String, HealthGauge]
  val handlers = new mutable.HashMap[String, HealthMessageHandler]

  /**
   * A handler for incoming health messages.
   */
  trait HealthMessageHandler {
    /**
     * Returns the messages received by this handler in order. By default, this returns Nil.
     */
    def getAll: Future[Seq[HealthGaugeMessage]] = Future(Nil)

    /**
     * Handles a new incoming message. By default, this does nothing, essentially meaning that the gauge will be
     * updated, but the message will not be persisted.
     */
    def +=(message: HealthGaugeMessage): Unit = {}
  }

  /**
   * Creates a new handler for messages with the given gauge id.
   */
  def newHandler(id: String): HealthMessageHandler

  override final def getAllHealthGauges: Seq[HealthGauge] = gauges.values.toSeq

  override final def getHealthGauge(id: String): HealthGauge =
    if (gauges contains id)
      gauges(id)
    else
      throw new ResourceNotFoundError(s"Unable to find resource $id")

  override final def createHealthGauge(m: HealthGaugeRecord): String = {
    val id = m.id
    if (gauges contains id)
      throw new UnprocessableEntityError(s"Resource with id $id already exists")
    else {
      val newGauge =
        HealthGauge(clock.dateNow(), "This gauge has not yet been updated", m.id, m.name, HealthSeverity.OK)
      gauges(id) = newGauge
      handlers(id) = newHandler(id)
      s"Successfully created resource $id"
    }
  }

  override final def getAllHealthMessages(id: String): Future[Seq[HealthGaugeMessage]] =
    if (handlers contains id) handlers.get(id).get.getAll else Future(Nil)

  override final def createHealthMessage(id: String, m: HealthGaugeMessageRecord): HealthGaugeMessage =
    if (gauges contains id) {
      val creationTime = clock.dateNow()
      val newGauge = HealthGauge(creationTime, m.message, id, gauges(id).name, m.severity)
      gauges(id) = newGauge
      val newMessage = HealthGaugeMessage(creationTime, UUID.randomUUID(), m.message, m.severity, m.sourceId)
      handlers(id) += newMessage
      newMessage
    } else
      throw new ResourceNotFoundError(s"Unable to find resource $id")

  override final def getSevereHealthGauges: Seq[HealthGauge] = {
    val sortedGauges = gauges.values.toSeq.filter(_.severity > HealthSeverity.OK).sortBy(_.severity)
    val highestGauge = sortedGauges.lastOption
    highestGauge match {
      case Some(gauge) => sortedGauges.filter(_.severity == gauge.severity)
      case None => Nil
    }
  }
}

/**
 * Concrete implementation of HealthDao that stores all messages in memory.
 */
class InMemoryHealthDao(clock: Clock) extends AbstractHealthDao(clock) {

  override final def newHandler(id: String) = new HealthMessageHandler {
    private val messages = new mutable.MutableList[HealthGaugeMessage]

    override def +=(message: HealthGaugeMessage): Unit = messages += message

    override def getAll: Future[Seq[HealthGaugeMessage]] = Future(messages.toSeq)
  }

  @VisibleForTesting
  def clear(): Unit = {
    gauges.clear()
    handlers.clear()
  }
}

/**
 * Provides an InMemoryHealthDao.
 */
trait InMemoryHealthDaoProvider extends HealthDaoProvider {
  this: ClockProvider =>

  override final val healthDao: Singleton[HealthDao] = Singleton(() => new InMemoryHealthDao(clock()))
}