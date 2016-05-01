package com.pacbio.common.time

import com.pacbio.common.dependency.Singleton
import org.joda.time.{DateTime => JodaDateTime, Instant => JodaInstant}

/**
 * Abstract interface for a clock that gives the current time.
 */
trait Clock {
  import PacBioDateTimeFormat.TIME_ZONE

  def now(): JodaInstant
  final def dateNow(): JodaDateTime = new JodaDateTime(now(), TIME_ZONE)
}

/**
 * Provides a singleton instance of a Clock. Concrete providers must implement the clock val.
 */
trait ClockProvider {
  val clock: Singleton[Clock]
}

/**
 * Implementation of the Clock trait that uses the current system time.
 */
class SystemClock extends Clock {
  def now(): JodaInstant = JodaInstant.now()
}

/**
 * Provides a singleton SystemClock.
 */
trait SystemClockProvider extends ClockProvider {
  override final val clock: Singleton[Clock] = Singleton(() => new SystemClock)
}

/**
 * Fake implementation of Clock designed for testing.
 * @param nowMillis the current time (in ms since epoch) that will be returned by the next call to now() or dateNow()
 * @param stepMillis the number of milliseconds the current time will be incremented by with a call to step()
 * @param autoStep if true, step() will automatically be called after every call to now or dateNow()
 */
class FakeClock(var nowMillis: Long, stepMillis: Long, autoStep: Boolean) extends Clock {
  def now(): JodaInstant = {
    val nowInstant = new JodaInstant(nowMillis)
    if (autoStep) step()
    nowInstant
  }

  def step(): Unit = nowMillis += stepMillis

  def reset(nowMillis: Long): Unit = this.nowMillis = nowMillis
}

/**
 * Provides a singleton FakeClock. Subclasses may override the default values of nowMillis (1000000000 ms), stepMillis
 * (1 ms), and autoStep (false).
 */
trait FakeClockProvider extends ClockProvider {
  val nowMillis = 1000000000
  val stepMillis = 1
  val autoStep = false

  override final val clock: Singleton[Clock] = Singleton(() => new FakeClock(nowMillis, stepMillis, autoStep))
}