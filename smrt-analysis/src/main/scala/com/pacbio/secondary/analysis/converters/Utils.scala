package com.pacbio.secondary.analysis.converters

import java.io.File
import java.nio.file.{Path, Files, Paths}
import scala.io.Source
import scala.util.matching.Regex
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._


/**
 * General File Utils
 */
object Utils extends LazyLogging {

  /**
   * Convert a FOFN path to a List of Files
   * @param fofn
   * @return
   */
  def fofnToFiles(fofn: Path): Seq[Path] = {
    Source.fromFile(fofn.toFile).getLines().map(Paths.get(_)).toList
  }

  def recursiveListFiles(f: File, rx: Regex): Array[File] = {
    val these = f.listFiles
    val good = these.filter(f => rx.findFirstIn(f.getName).isDefined)
    good ++ these.filter(_.isDirectory).flatMap(recursiveListFiles(_, rx))
  }

  def recursiveScan(rootDir: String): Either[DatasetConvertError, List[File]] = {
    val path = Paths.get(rootDir)
    val f = path.toFile
    // m110106_050924_00114_c000000062550000000300000112311181_s2_p0.metadata.xml
    val matcher = """.metadata.xml""".r

    val result = if (Files.exists(path) && Files.isDirectory(path)) {
      val xmlFiles = recursiveListFiles(f, matcher)
      Right(xmlFiles.toList)
    } else {
      Left(DatasetConvertError(s"Unable to find '$rootDir'"))
    }
    result
  }

}
