package com.pacbio.common.loaders

import java.io.File
import spray.json._

import com.pacbio.common.models.{PacBioComponentManifest, PacBioJsonProtocol}

/**
  * Created by mkocher on 8/7/16.
  */
trait ManifestLoader {

  // Putting these constants here for now
  val CONFIG_KEY = "pb-services.manifest-file"

  // SL-UI component id
  val SL_UI_ID = "smrtlink_ui"

  // SL "component" id (this is a bit unclear. This should be the "system" component id and version and shouldn't
  // be confused with the services version)
  val SL_ID = "smrtlink"

  import PacBioJsonProtocol._

  def loadFrom(file: File): Seq[PacBioComponentManifest] =
    scala.io.Source.fromFile(file)
      .mkString.parseJson
      .convertTo[Seq[PacBioComponentManifest]]


}

object ManifestLoader extends  ManifestLoader

