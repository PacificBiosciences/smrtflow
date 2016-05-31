package com.pacbio.common.actors

import java.util.{Properties, UUID}

import akka.actor.{ActorRef, Props}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.time.{Clock, ClockProvider}
import org.joda.time.{Duration => JodaDuration, Instant => JodaInstant}
import scala.collection.JavaConverters._


/**
 * Companion object for the StatusServiceActor class, defining the set of messages it can handle.
 */

object StatusServiceActor {
  case object GetUptime
  case object GetBaseServiceId
  case object GetUser
  case object GetUUID
  case object GetBuildVersion
}

/**
 * Akka actor that can provide basic status info. (Currently only provides the start time of the
 * system.)
 */
class StatusServiceActor(clock: Clock,
                         baseServiceId: String,
                         uuid: UUID,
                         buildVersion: String) extends PacBioActor {

  val startedAt: JodaInstant = clock.now()
  def uptimeMillis: Long = new JodaDuration(startedAt, clock.now()).getMillis

  import StatusServiceActor._

  def receive: Receive = {
    case GetUptime        => respondWith(uptimeMillis)
    case GetBaseServiceId => respondWith(baseServiceId)
    case GetUser          => respondWith(System.getenv("USER"))
    case GetUUID          => respondWith(uuid)
    case GetBuildVersion  => respondWith(buildVersion)
  }
}

sealed trait BaseStatusServiceActorProvider {
 /**
  * Should be initialized at the top-level with
  * {{{override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)}}}
  */
  val buildPackage: Singleton[Package]

 /**
  * Should be initialized at the top-level with a base id for the total set of services. For instance, if you want your
  * service package to have id "pacbio.smrtservices.smrtlink_analysis", you would initialize this like so:
  * {{{override val baseServiceId: Singleton[String] = Singleton("smrtlink_analysis")}}}
  */
  val baseServiceId: Singleton[String]

  val uuid: Singleton[UUID] = Singleton(UUID.randomUUID())

  val buildVersion: Singleton[String] = Singleton(() => {
      val files = getClass().getClassLoader().getResources("version.properties")
      if (files.hasMoreElements) {
        val in = files.nextElement().openStream()
        try {
          val prop = new Properties
          prop.load(in)
          prop.getProperty("version").replace("SNAPSHOT", "") + prop.getProperty("sha1").substring(0, 7)
        }
        finally {
          in.close()
        }
      }
      else {
        "unknown version"
      }
    }
  )
}


/**
 * Provides a singleton ActorRef for a StatusServiceActor. Concrete providers must mixin a ClockProvider and an
 * ActorRefFactoryProvider.
 */
trait StatusServiceActorRefProvider extends BaseStatusServiceActorProvider {
  this: ClockProvider with ActorRefFactoryProvider =>

  val statusServiceActorRef: Singleton[ActorRef] = Singleton(() =>
      actorRefFactory().actorOf(Props(classOf[StatusServiceActor], clock(), baseServiceId(), uuid(), buildVersion())))
}

/**
 * Provides a singleton StatusServiceActor. Concrete providers must mixin a ClockProvider. Note that this provider is
 * designed for tests, and should generally not be used in production. To create a production app, use the
 * {{{StatusServiceActorRefProvider}}}.
 */
trait StatusServiceActorProvider extends BaseStatusServiceActorProvider {
  this: ClockProvider =>

  val statusServiceActor: Singleton[StatusServiceActor] =
      Singleton(() => new StatusServiceActor(clock(), baseServiceId(), uuid(), buildVersion()))
}
