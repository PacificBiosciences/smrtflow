package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.actors.{AlarmDaoProvider, AlarmDao}
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class AlarmService(alarmDao: AlarmDao, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("alarm"),
    "Alarm Service",
    "0.1.0", "Subsystem Alarm Service")

  val alarmServiceName = "alarm"

  val routes =
    pathPrefix(alarmServiceName) {
      authenticate(authenticator.wso2Auth) { user =>
        pathEndOrSingleSlash {
          get {
            complete {
              ok {
                alarmDao.getAlarms
              }
            }
          }
        } ~
        path("status") {
          get {
            complete {
              ok {
                alarmDao.getAlarmStatuses
              }
            }
          }
        } ~
        pathPrefix(Segment) { id =>
          pathEndOrSingleSlash {
            get {
              complete {
                ok {
                  alarmDao.getAlarm(id)
                }
              }
            }
          } ~
          path("status") {
            get {
              complete {
                ok {
                  alarmDao.getAlarmStatus(id)
                }
              }
            }
          }
        }
      }
    }
}

/**
 * Provides a singleton AlarmService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{AlarmDaoProvider}}} and an {{{AuthenticatorProvider}}}.
 */
trait AlarmServiceProvider {
  this: AlarmDaoProvider with AuthenticatorProvider =>

  final val alarmService: Singleton[AlarmService] =
    Singleton(() => new AlarmService(alarmDao(), authenticator())).bindToSet(AllServices)
}

trait AlarmServiceProviderx {
  this: AlarmDaoProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  final val alarmService: Singleton[AlarmService] =
    Singleton(() => new AlarmService(alarmDao(), authenticator()))

  addService(alarmService)
}