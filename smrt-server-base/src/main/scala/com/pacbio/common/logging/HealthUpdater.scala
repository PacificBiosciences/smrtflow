package com.pacbio.common.logging

import akka.actor.ActorRef
import com.pacbio.common.actors.HealthServiceActor.{CreateGauge, CreateMessage}
import com.pacbio.common.actors.HealthServiceActorRefProvider
import com.pacbio.common.dependency.{SetBindings, SetBinding, Singleton}
import com.pacbio.common.models.{HealthGaugeRecord, LogLevel, HealthGaugeMessageRecord, HealthSeverity}

import scala.collection.mutable

/**
 * Trait designed to simplify interaction with the Health Service.
 */
trait HealthUpdater {
  import HealthSeverity._

  val healthId: String
  val sourceId: String
  def update(msg: String, severity: HealthSeverity, sourceId: String)

  def ok(msg: String) = update(msg, OK, sourceId)
  def caution(msg: String) = update(msg, CAUTION, sourceId)
  def alert(msg: String) = update(msg, ALERT, sourceId)
  def critical(msg: String) = update(msg, CRITICAL, sourceId)
}

/**
 * Implementation of HealthUpdater that sends akka messages to the Health Service Actor.
 *
 * @param healthId The id of the log resource that this logger will send messages to. If auto-logging is enabled, this
 *                 will also be used as the log resource id.
 * @param sourceId The id of the source of the health updates. If auto-logging is enabled, this will also be used as the
 *                 log source id.
 * @param healthActor The ActorRef where the health service actor can be found
 * @param autoLoggingFactory If present, messages will also be sent to the Log Service
 */
class HealthUpdaterImpl(
    override val healthId: String,
    override val sourceId: String,
    // TODO(smcclellan): Use DAO directly, bypass actor
    healthActor: ActorRef,
    autoLoggingFactory: Option[LoggerFactory]) extends HealthUpdater {

  import HealthSeverity._

  val severityToLogLevel: Map[HealthSeverity, LogLevel.LogLevel] = Map(
    OK -> LogLevel.DEBUG,
    CAUTION -> LogLevel.NOTICE,
    ALERT -> LogLevel.WARN,
    CRITICAL -> LogLevel.CRITICAL
  )

  override def update(msg: String, severity: HealthSeverity, sourceId: String) = {
    healthActor ! CreateMessage(healthId, HealthGaugeMessageRecord(msg, severity, sourceId))
    autoLoggingFactory.foreach(f => f.getLogger(healthId, sourceId).log(msg, severityToLogLevel(severity)))
  }
}

/**
 * Trait for producing HealthUpdaters
 */
trait HealthUpdaterFactory {
  def getUpdater(healthId: String, sourceId: String): HealthUpdater
}

/**
 * SetBinding for initial health gauges. Providers may contribute a health gauge to be initiialized by binding a
 * HealthGaugeRecord to this.
 */
object HealthGauges extends SetBinding[HealthGaugeRecord]

/**
 * Abstract provider that provides a Singleton HealthUpdaterFactory
 */
trait HealthUpdaterFactoryProvider {
  this: SetBindings =>

  /**
   * Provides a singleton HealthUpdaterFactory
   */
  val healthUpdaterFactory: Singleton[HealthUpdaterFactory]

  /**
   * Defines a set of initial gauges for the HealthDao
   */
  val healthGauges: Singleton[Set[HealthGaugeRecord]] = Singleton(() => set(HealthGauges))
}

/**
 * Concrete HealthUpdaterFactory that provides a HealthUpdaterImpl.
 *
 * @param healthActor The ActorRef where the health service actor can be found
 * @param autoLoggingFactory If present, messages will also be sent to the Log Service
 * @param healthGauges Set of records for initializing health gauges
 */
class HealthUpdaterFactoryImpl(
    healthActor: ActorRef,
    autoLoggingFactory: Option[LoggerFactory],
    healthGauges: Set[HealthGaugeRecord]) extends HealthUpdaterFactory {

  val healthGaugesById: Map[String, HealthGaugeRecord] = healthGauges.map(g => g.id -> g).toMap
  val initializedIds: mutable.Set[String] = new mutable.HashSet

  override def getUpdater(healthId: String, sourceId: String): HealthUpdater = {
    if (!initializedIds.contains(healthId)) healthActor ! CreateGauge(healthGaugesById(healthId))
    new HealthUpdaterImpl(healthId, sourceId, healthActor, autoLoggingFactory)
  }
}

/**
 * Provides a HealthUpdaterFactoryImpl, with no auto-logging. Concrete providers must mixin a
 * HealthServiceActorRefProvider and SetBindings.
 */
trait HealthUpdaterFactoryImplProvider extends HealthUpdaterFactoryProvider {
  this: HealthServiceActorRefProvider with SetBindings =>

  override val healthUpdaterFactory: Singleton[HealthUpdaterFactory] =
    Singleton(() => new HealthUpdaterFactoryImpl(healthServiceActorRef(), None, healthGauges()))
}

/**
 * Provides a HealthUpdaterFactoryImpl, with auto-logging. Concrete providers must mixin a
 * HealthServiceActorRefProvider, a LoggerFactoryProvider, and SetBindings.
 */
trait HealthUpdaterFactoryImplWithAutoLoggingProvider extends HealthUpdaterFactoryProvider {
  this: HealthServiceActorRefProvider with LoggerFactoryProvider with SetBindings =>

  override val healthUpdaterFactory: Singleton[HealthUpdaterFactory] =
    Singleton(() => new HealthUpdaterFactoryImpl(healthServiceActorRef(), Some(loggerFactory()), healthGauges()))
}