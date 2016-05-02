package com.pacbio.secondary.smrtserver.services

import java.nio.file.Paths

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobsBaseMicroService
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object DataSetImporterJsonProtocol extends DefaultJsonProtocol {

  case class SubreadPostRecord(path: String)
  case class ReferencePostRecord(path: String)

  implicit val subreadPostRecordFormat = jsonFormat1(SubreadPostRecord)
  implicit val referencePostRecordFormat = jsonFormat1(ReferencePostRecord)

}

/**
 * ############ This is DEPRECIATED but kept around for simple importing into the system ###################
 *
 * @param dbActor DAO actor
 */
class DataSetImportService(dbActor: ActorRef) extends JobsBaseMicroService {

  import DataSetImporterJsonProtocol._
  import SecondaryAnalysisJsonProtocols._

  implicit val timeout = Timeout(120.seconds)
  val basePrefix = "importer"
  val convertBasePrefix = "converter"
  val scanPrefix = "scan"

  val manifest = PacBioComponentManifest(
    toServiceId("secondary.scan_importer.dataset"),
    "Secondary DataSet Importing Service", "0.2.0",
    "Secondary DataSet Import/Scan Service",
    None)

  val routes =
    pathPrefix(basePrefix) {
      path("status") {
        get {
          complete {
            SimpleStatus(manifest.id, s"Service ${manifest.name}", 12345)
          }
        }
      } ~
      path("subread") {
        post {
          entity(as[SubreadPostRecord]) { r =>
            complete {
              s"Import subread dataset (movie.metadata.xml) with $r"
            }
          }
        }
      } ~
      path("subread" / "scan") {
        post {
          entity(as[SubreadPostRecord]) { r =>
            complete {
              s"Run subread SCAN with $r"
            }
          }
        }
      } ~
      path("reference") {
        post {
          entity(as[ReferencePostRecord]) { r =>
            respondWithMediaType(MediaTypes.`application/json`) {
              complete {
                created {
                  (dbActor ? ImportReferenceDataSetFromFile(r.path)).mapTo[String]
                }
              }
            }
          }
        }
      } ~
      path("subread") {
        post {
          entity(as[SubreadPostRecord]) { r =>
            respondWithMediaType(MediaTypes.`application/json`) {
              complete {
                created {
                  (dbActor ? ImportSubreadDataSetFromFile(r.path)).mapTo[String]
                }
              }
            }
          }
        }
      } ~
      pathPrefix(convertBasePrefix) {
        // This doesn't actually import into the db yet. It only converts it
        path("reference") {
          post {
            entity(as[ReferencePostRecord]) { r =>
              respondWithMediaType(MediaTypes.`application/json`) {
                complete {
                  created {
                    (dbActor ? ConvertReferenceInfoToDataset(r.path, Paths.get("tmp.dataset.xml"))).mapTo[String]
                  }
                }
              }
            }
          }
        }
      }
    }
}