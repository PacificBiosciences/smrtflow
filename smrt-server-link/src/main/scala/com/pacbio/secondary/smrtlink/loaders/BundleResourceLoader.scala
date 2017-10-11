package com.pacbio.secondary.smrtlink.loaders

import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.typesafe.scalalogging.LazyLogging

/**
  * Loader for resources located in an external bundle of known structure
  */
trait BundleResourceLoader[T]
    extends EnvResourceLoader[T]
    with SmrtLinkJsonProtocols
    with LazyLogging {
  val BUNDLE_ENV_VAR: String
  val ROOT_DIR_PREFIX: String

  def loadResourcesFromBundle: Seq[T] = {
    scala.util.Properties
      .envOrNone(BUNDLE_ENV_VAR)
      .map(v => Paths.get(v))
      .map(p => p.resolve(ROOT_DIR_PREFIX).toAbsolutePath.toString)
      .map(parseValue)
      .getOrElse(Seq.empty[T])
  }

  def loadResources: Seq[T] = loadResourcesFromEnv ++ loadResourcesFromBundle
}
