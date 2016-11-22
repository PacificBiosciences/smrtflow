package com.pacbio.secondary.smrtlink.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._

import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.actors.ActorSystemProvider
import com.pacbio.secondary.analysis.engine.CommonMessages._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.common.services._


class EulaService(dbActor: ActorRef,  authenticator: Authenticator)//(implicit val actorSystem: ActorSystem)
    extends BaseSmrtService with JobServiceConstants {

  import JobsDaoActor._
  import SmrtLinkJsonProtocols._

  val manifest = PacBioComponentManifest(
    toServiceId("eula"),
    "EULA Service",
    "0.1.0", "End-User License Agreement Service")

  implicit val timeout = Timeout(20.seconds)

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
                (dbActor ? AcceptEula(sopts.user, sopts.smrtlinkVersion, sopts.enableInstallMetrics, sopts.enableJobMetrics)).mapTo[EulaRecord]
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
    with AuthenticatorProvider
    with ServiceComposer =>

  val eulaService: Singleton[EulaService] =
    Singleton { () =>
      new EulaService(jobsDaoActor(), authenticator())
    }

  addService(eulaService)
}
