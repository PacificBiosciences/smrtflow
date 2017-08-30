package com.pacbio.secondary.smrtlink.loaders

import java.io.File
import java.nio.file.{Path, Paths}

import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}

/**
  * Created by mkocher on 7/12/16.
  */
trait EnvResourceLoader[T] extends ResourceLoaderBase[T] with LazyLogging{

  // This will point to a directory or a list of directories
  // example MY_VAR=/path/to/stuff:/path/to/resources
  val ENV_VAR: String

  def getListOfFiles(dir: Path): Seq[File] = {
    val d = dir.toFile
    if (d.exists && d.isDirectory) {
      d.listFiles
          .filter(_.isFile)
          .map(_.toPath.toAbsolutePath.toFile)
    } else {
      Seq.empty[File]
    }
  }

  def loadFrom(file: File): Option[T] = {
    Try {
      loadFromString(scala.io.Source.fromFile(file).mkString)
    } match {
      case Success(r) =>
        logger.info(s"${loadMessage(r)} from ${file.toPath}")
        Option(r)
      case Failure(ex) =>
        logger.error(s"Failed to load resource from ${file.toPath}. Error ${ex.getMessage}")
        None
    }
  }

  def loadFromDir(path: Path): Seq[T] = {
    getListOfFiles(path)
        .filter(_.toString.endsWith(".json"))
        .flatMap(f => loadFrom(f))
  }

  def parseValue(sx: String): Seq[T] =
    sx.split(":")
        .map(p => Paths.get(p))
        .flatMap(loadFromDir)

  def loadResourcesFromEnv: Seq[T] = {
    scala.util.Properties
        .envOrNone(ENV_VAR)
        .map(parseValue)
        .getOrElse(Seq.empty[T])
  }
}
