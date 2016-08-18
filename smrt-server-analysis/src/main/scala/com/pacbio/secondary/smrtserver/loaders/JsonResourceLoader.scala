package com.pacbio.secondary.smrtserver.loaders

import java.util.regex.Pattern
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols

import collection.JavaConversions._

import com.pacbio.secondary.smrtserver.resourceutils.ResourceList
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{Charsets, IOUtils}

import scala.util.Try

import scala.language.postfixOps
import scala.util.control.NonFatal

/**
 * The Loading of Resources is Really annoying in Java. This is to get around
 * loading resources from running within sbt, or running from a single Jar file
 * There's also not an easy way to list a directory resources without a bit of
 * manual work.
 *
 * All of the JSON protocols are loaded, so 
 */
trait JsonResourceLoader[T] extends ResourceLoaderBase[T] with SecondaryAnalysisJsonProtocols with LazyLogging{

  // Root Directory of the resources within the CLASSPATH
  // example, "resolved-pipeline-templates"
  val ROOT_DIR_PREFIX: String

  /**
   * Loads Resources for sbt model
   */
  def loadResourcesForSbt: Seq[T] = {
    // This does work
    // The leading '/' is required for sbt, but not for loading from assembly
    val xs = this.getClass.getClassLoader.getResourceAsStream(s"$ROOT_DIR_PREFIX/")
    logger.info(s"sbt Resources Loader. Resolved xs $xs")
    val files = IOUtils.readLines(xs, Charsets.UTF_8)
    logger.info(s"Files files ${files.length} $files")
    val pts = files.map(x => loadItemFromResourceDir(s"$ROOT_DIR_PREFIX/$x"))
    logger.info(s"Resource Loader (for sbt) loaded ${pts.length} pipelines")
    pts
  }

  /**
   * Loads Resources from a single jar (i.e., built from assembly)
 *
   * @return
   */
  def loadResourceFromJar: Seq[String] = {
    val px = Pattern.compile(".*")
    val rs = ResourceList.getResources(px).filter(x => x.startsWith(ROOT_DIR_PREFIX) && x.endsWith(".json")).toList
    logger.info(s"Resources from jar found ${rs.length} from $ROOT_DIR_PREFIX $px")
    rs
  }

  def loadItemFromResourceDir(xs: String): T = {
    logger.debug(s"Loading resource from $xs")
    // Must have the prefixed '/'
    val sx = getClass.getResourceAsStream("/" + xs)
    val theString = IOUtils.toString(sx, "UTF-8")
    val pt = loadFromString(theString)
    logger.debug(s"${loadMessage(pt)} from $xs")
    pt
  }

  def loadItemsFromResourceDir: Seq[T] = {
    val pts = loadResourceFromJar.map(loadItemFromResourceDir)
    logger.info(s"Loaded ${pts.length} resources.")
    pts
  }

  /**
   * Loads Either the Pipelines using SBT compatible model, from a single jar methods.
   * I have no idea why this is so complicated.
   *
   * If the resources can't be loaded from the SBT, the load from the resources assembly use case
   *
   * @return
   */
  def loadResources: Seq[T] = {
    // If non-empty resource list found at SBT path, return them
    val sbtResources = Try(loadResourcesForSbt)
    if (sbtResources.toOption.exists(_.nonEmpty)) {
      logger.info("Found non-empty resource list on SBT resource path.")
      return sbtResources.get
    }

    // If non-empty resource list found at JAR path, return them
    val jarResources = Try(loadItemsFromResourceDir)
    if (jarResources.toOption.exists(_.nonEmpty)){
      logger.info("Found non-empty resource list on JAR resource path.")
      return jarResources.get
    }

    // If empty resource list found at both paths, return empty list
    if (sbtResources.toOption.exists(_.isEmpty) && jarResources.toOption.exists(_.isEmpty)) {
      logger.info("Found empty resource list on both SBT and JAR resource paths.")
      return Nil
    }

    // If this point is reached, one of the Trys must be a failure, so propagate the first error found
    sbtResources.get
    jarResources.get
  }
}
