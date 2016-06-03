package com.pacbio.secondary.lims.services

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.ServiceComposer
import spray.http.MultipartFormData

/**
 * Import service for lims.yml files
 *
 * RESTful endpoint to import lims.yml files.
 */
class ImportService extends LimsService {

  val manifest = PacBioComponentManifest(
    toServiceId("smrtlink_lims_import"),
    name="Import lims.yml",
    version="0.0.1",
    description="Imports lims.yml files.")

  // TODO: lots of code below to simply map PUT to an import...simplify this?
  val routes =
    get {
      parameters('file) {
        (file) =>
          complete {
            s"Importing...! $file"
          }
      }
    } ~
    post {
      entity(as[MultipartFormData]) {
        formData => {
          formData.fields.foreach(f => loadData(f.entity.data.toByteArray))
          complete("OK")
          //complete("POST import!")
        }
      }
    }

  /**
   * Converts the YML to a Map.
   * @param bytes
   * @return
   */
  def loadData(bytes: Array[Byte]): Unit = {
    println(new String(bytes))
  }
}

trait ImportProvider extends ServiceComposer {
  //this: LimsDaoProvider with ServiceComposer =>

  final val importService: Singleton[ImportService] =
    Singleton(() => new ImportService())

  addService(importService)
}