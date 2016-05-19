package com.pacbio.secondaryinternal

import java.nio.file.Path

import scala.io.Source

import com.pacbio.secondaryinternal.models._
import com.github.tototoshi.csv._


object IOUtils {

  def parseConditionCsv(path: Path): Seq[ServiceCondition] =
    parseConditionCsv(Source.fromFile(path.toFile))


  def parseConditionCsv(sx: Source): Seq[ServiceCondition] = {
    val reader = CSVReader.open(sx)
    val datum = reader.allWithHeaders()
    // FIXME(mpkocher)(2016-4-17) Add format for the host of "smrtlink-beta:9999"
    datum.map(x => ServiceCondition(x("condId"), x("host"), 8081, x("jobId").toInt))
  }
}