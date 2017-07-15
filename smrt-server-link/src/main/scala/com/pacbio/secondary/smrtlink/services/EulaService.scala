package com.pacbio.secondary.smrtlink.services

import java.nio.file.{Files, Paths}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.engine.CommonMessages._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.common.services._
import com.pacbio.common.utils.OSUtils
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.SystemUtils
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.Future


class EulaService(smrtLinkSystemVersion: Option[String], dbActor: ActorRef,  authenticator: Authenticator)
    extends BaseSmrtService with JobServiceConstants with OSUtils {

  import JobsDaoActor._
  import SmrtLinkJsonProtocols._

  val manifest = PacBioComponentManifest(
    toServiceId("eula"),
    "EULA Service",
    "0.1.0", "End-User License Agreement Service")

  implicit val timeout = Timeout(30.seconds)

  def toEulaRecord(user: String, enableInstallMetrics: Boolean, systemVersion: String): EulaRecord = {
    val osVersion = getOsVersion()
    val acceptedAt = JodaDateTime.now()
    EulaRecord(user, acceptedAt, systemVersion, osVersion, enableInstallMetrics, enableJobMetrics = false)
  }

  def convertToEulaRecord(user: String, enableInstallMetrics: Boolean): Future[EulaRecord] = {
    smrtLinkSystemVersion match {
      case Some(version) => Future.successful(toEulaRecord(user, enableInstallMetrics, version))
      case _ => Future.failed(throw new ResourceNotFoundError("System was not configured with SMRT Link System version. Unable to accept Eula"))
    }
  }

  override val routes =
    pathPrefix("eula") {
      path(Segment) { version =>
        get {
          complete {
            ok {
              (dbActor ? GetEulaByVersion(version)).mapTo[EulaRecord]
            }
          }
        } ~
        delete {
          complete {
            ok {
              (dbActor ? DeleteEula(version)).mapTo[SuccessMessage]
            }
          }
        }
      } ~
      pathEndOrSingleSlash {
        post {
          entity(as[EulaAcceptance]) { sopts =>
            complete {
              created {
                for {
                  eulaRecord <- convertToEulaRecord(sopts.user, sopts.enableInstallMetrics)
                  acceptedRecord <-  (dbActor ? AddEulaRecord(eulaRecord)).mapTo[EulaRecord]
                } yield acceptedRecord
              }
            }
          }
        } ~
        get {
          complete {
            ok {
              (dbActor ? GetEulas).mapTo[Seq[EulaRecord]]
            }
          }
        }
      }
    }

}

trait EulaServiceProvider {
  this: JobsDaoActorProvider
    with SmrtLinkConfigProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  val eulaService: Singleton[EulaService] =
    Singleton { () =>
      new EulaService(smrtLinkVersion(), jobsDaoActor(), authenticator())
    }

  addService(eulaService)
}
