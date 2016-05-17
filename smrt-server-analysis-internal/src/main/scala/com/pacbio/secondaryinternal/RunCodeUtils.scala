package com.pacbio.secondaryinternal

import com.pacbio.secondaryinternal.models.RuncodeResolveError

import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions._
import java.nio.file.{Paths, Files, FileSystems, Path}

import scala.collection.mutable
import scala.util.matching.Regex

object RunCodeUtils {

  def getSubdirs(rootPath: String): List[Path] = {
    val p = FileSystems.getDefault.getPath(rootPath)

    var volumes = mutable.MutableList[Path]()

    if(Files.exists(p)) {
      val ds = Files.newDirectoryStream(p, "data*")

      // I don't know why map doesn't work on this
      val it = ds.iterator()
      it.foreach(x => volumes += x.toAbsolutePath)
    }

    volumes.toList
  }

  def getSubdirs2(rootPath: String): List[Path] = {
    val p = FileSystems.getDefault.getPath(rootPath)

    var volumes = mutable.MutableList[Path]()

    if(Files.exists(p)) {
      val ds = Files.newDirectoryStream(p)

      // I don't know why map doesn't work on this
      val it = ds.iterator()
      it.foreach(x => volumes += x.toAbsolutePath)
    }

    volumes.toList
  }

  def findRuncodeDir(volumePaths: List[Path], prefix: String, p: String): Option[Path] = {

    def toP(v: Path): Path = {
      val vp = v.toAbsolutePath
      Paths.get(s"$vp/$prefix/$p")
    }

    def f(path: Path): Option[Path] = if(Files.exists(path)) Some(path) else None

    // println(volumePaths)
    // This should only resolve to one value
    val validPaths = volumePaths.map(x => toP(x)).map(x => f(x))
    // println(validPaths)
    val v2 = validPaths.flatMap(x => x)
    // println(v2)
    if (v2.isEmpty) None else Option(v2.toList(0))
  }

  def resolver(runcode: String): Either[RuncodeResolveError, Path] = {
    var parsedValue = None: Option[(String, String)]

    val mntDirs = getSubdirs("/mnt")
    val volumes = mntDirs.map(x => getSubdirs2(x.toAbsolutePath.toString)).flatMap(x => x)
    //logger.debug(volumes.toString())

    val e = RuncodeResolveError(runcode, s"Unable to resolve runcode '$runcode'")
    val pv = parseRunCode(runcode)

    pv match {
      case Some((a: String, b: String)) =>
        findRuncodeDir(volumes, a, b) match {
          case Some(p: Path) => Right(p)
          case _ => Left(e)
        }
      case _ => Left(e)
    }
  }

  def isValidRuncode(runcode: String): Boolean = {
    val r = new Regex("^[0-9]{7}-[0-9]{4}$")
    val s = r.findFirstIn(runcode).isEmpty
    !s
  }

  def parseRunCode(runcode: String): Option[(String, String)] = {
    var parsedValue = None: Option[(String, String)]

    if(isValidRuncode(runcode: String)){
      val arrayString = runcode.split('-')
      parsedValue = Option(arrayString(0), arrayString(1))
    }
    parsedValue
  }


}