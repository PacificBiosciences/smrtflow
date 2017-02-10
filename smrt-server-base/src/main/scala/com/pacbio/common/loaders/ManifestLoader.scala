package com.pacbio.common.loaders

import java.io.File
import java.nio.file.Paths

import spray.json._
import com.pacbio.common.models.{PacBioComponentManifest, PacBioJsonProtocol}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Try,Failure, Success}

import collection.JavaConversions._
import collection.JavaConverters._

/**
  * Created by mkocher on 8/7/16.
  */
trait ManifestLoader extends LazyLogging{

  // Putting these constants here for now
  // application.conf id
  val CONFIG_KEY = "smrtflow.server.manifestFile"
  // SL Tools component id
  val SMRT_LINK_TOOLS_ID = "smrttools"
  // SL "component" id (this is a bit unclear. This should be the "system" component id and version and shouldn't
  // be confused with the services version)
  val SMRTLINK_ID = "smrtlink"

  import PacBioJsonProtocol._

  def loadFrom(file: File): Seq[PacBioComponentManifest] =
    scala.io.Source.fromFile(file)
      .mkString.parseJson
      .convertTo[Seq[PacBioComponentManifest]]

  def loadFromConfig(config: Config): Seq[PacBioComponentManifest] =
    Try { loadFrom(Paths.get(config.getString(CONFIG_KEY)).toFile) } match {
      case Success(m) =>
        logger.info(s"Loaded manifests $m")
        m
      case Failure(ex) =>
        logger.warn(s"Failed to load pacbio-manifest.json from config key $CONFIG_KEY Error ${ex.getMessage}")
        Seq.empty[PacBioComponentManifest]
    }

}

object ManifestLoader extends  ManifestLoader

