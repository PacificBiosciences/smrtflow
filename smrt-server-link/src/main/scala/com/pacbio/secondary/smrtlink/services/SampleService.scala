package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.secondary.smrtlink.auth.{
  Authenticator,
  AuthenticatorProvider
}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{
  SampleServiceActor,
  SampleServiceActorRefProvider
}
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import com.pacbio.secondary.smrtlink.services.utils.SmrtDirectives

import scala.concurrent.ExecutionContext.Implicits._

/**
  * Created by devans on 4/12/16 cribbing primarily from RunDesignService and related
  *
  * The purpose of these end points is to provide a simple CRUD backing store for the
  * SampleSetup module in SMRT Link. Each sample stored is a simple JSON blob of
  * input values, about 10 of them. And samples are keyed by GUID and Name, the latter
  * only for future filtering/searching if we need it. We'll also allow searching
  * by date ultimately to help scale the number of saved samples and display the
  * most recent by default.
  */
class SampleService(sampleActor: ActorRef)
    extends SmrtLinkBaseMicroService
    with SmrtLinkJsonProtocols {

  import SampleServiceActor._

  val manifest = PacBioComponentManifest(toServiceId("samples"),
                                         "Subsystem Sample Service",
                                         "0.1.0",
                                         "Subsystem Sample Service")

  // This testing model is a bit suspect
  val routes =
    path("samples/test/clear") {
      post {
        complete {
          logger.info("Request to clear our database of samples for testing")
          "Request to clear our database of samples for testing"
        }
      }
    } ~ path("samples/test/fill") {
      post {
        entity(as[SampleTestExamples]) { ste =>
          complete {
            StatusCodes.Created -> {
              logger.info(
                s"Request to fill our database with ${ste.count} test samples")
              "Created"
            }
          }
        }
      }
    } ~
      pathPrefix("samples") {
        pathEnd {
          get {
            complete {
              (sampleActor ? GetSamples()).mapTo[Set[Sample]]
            }
          } ~
            post {
              SmrtDirectives.extractRequiredUserRecord { user =>
                entity(as[SampleCreate]) { create =>
                  complete {
                    StatusCodes.Created -> {
                      (sampleActor ? CreateSample(user.userId, create))
                        .mapTo[Sample]
                    }
                  }
                }
              }
            }
        } ~
          pathPrefix(JavaUUID) { uniqueId =>
            pathEnd {
              get {
                complete {
                  (sampleActor ? GetSample(uniqueId)).mapTo[Sample]
                }
              } ~
                post {
                  entity(as[SampleUpdate]) { update =>
                    complete {
                      (sampleActor ? UpdateSample(uniqueId, update))
                        .mapTo[Sample]
                    }
                  }
                } ~
                delete {
                  complete {
                    (sampleActor ? DeleteSample(uniqueId))
                      .mapTo[MessageResponse]
                  }
                }
            }
          }
      }
}

/**
  * Provides a singleton SampleService, and also binds it to the set of total services. Concrete providers must mixin
  * a {{{SampleServiceActorRefProvider}}} and an {{{AuthenticatorProvider}}}.
  */
trait SampleServiceProvider {
  this: SampleServiceActorRefProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  final val sampleService: Singleton[SampleService] =
    Singleton(() => new SampleService(sampleServiceActorRef()))

  addService(sampleService)
}
