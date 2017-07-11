package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.secondary.smrtlink.actors.{AlarmDaoActor, AlarmDaoActorProvider, DaoFutureUtils}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.ServiceComposer
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing.PathMatchers.Segment

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class AlarmService(alarmDaoActor: ActorRef) extends SmrtLinkBaseMicroService with DefaultJsonProtocol with DaoFutureUtils{

  import PacBioJsonProtocol._
  import AlarmDaoActor._

  val manifest = PacBioComponentManifest(
    toServiceId("alarm"),
    "Alarm Service",
    "0.1.0", "Subsystem Alarm Service of the Alarm Runners and Alarm Status")

  val alarmServiceName = "alarms"

  val alarmRoutes =
    pathPrefix(alarmServiceName) {
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              (alarmDaoActor ? GetAllAlarmStatus).mapTo[Seq[AlarmStatus]]
            }
          }
        }
      } ~
      path(Segment) { alarmId =>
        get {
          complete {
            ok {
              for {
                opt <- (alarmDaoActor ? GetAlarmStatusById(alarmId)).mapTo[Option[AlarmStatus]]
                status <- failIfNone[AlarmStatus](s"Unable to find Alarm Id $alarmId")(opt)
              } yield status
            }
          }
        }
      }
    }

  val routes = alarmRoutes
}

trait AlarmServiceProvider {
  this: ActorRefFactoryProvider with AlarmDaoActorProvider with ServiceComposer =>

  val alarmService: Singleton[AlarmService] = Singleton(() => new AlarmService(alarmDaoActor()))

  addService(alarmService)
}
