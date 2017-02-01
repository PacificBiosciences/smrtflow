package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.actors.{AlarmDaoProvider, AlarmDao}
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
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
    "1.0.0", "Subsystem Alarm Service")

  val alarmServiceName = "alarm"

  val routes =
    pathPrefix(alarmServiceName) {
      authenticate(authenticator.wso2Auth) { user =>
        pathEnd {
          get {
            complete {
              ok {
                alarmDao.getUnhealthyMetrics
              }
            }
          }
        } ~
        pathPrefix("metrics") {
          pathEnd {
            get {
              complete {
                ok {
                  alarmDao.getAllAlarmMetrics
                }
              }
            } ~
            post {
              entity(as[AlarmMetricCreateMessage]) { m =>
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
                    created {
                      alarmDao.createAlarmMetric(m)
                    }
                  }
                }
              }
            }
          } ~
          pathPrefix(Segment) { id =>
            pathEnd {
              get {
                complete {
                  ok {
                    alarmDao.getAlarmMetric(id)
                  }
                }
              }
            } ~
            path("updates") {
              get {
                complete {
                  ok {
                    alarmDao.getMetricUpdates(id)
                  }
                }
              }
            }
          }
        } ~
        path("updates") {
          get {
            complete {
              ok {
                alarmDao.getAllUpdates
              }
            }
          } ~
          post {
            entity(as[AlarmMetricUpdateMessage]) { m =>
              complete {
                created {
                  alarmDao.update(m)
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