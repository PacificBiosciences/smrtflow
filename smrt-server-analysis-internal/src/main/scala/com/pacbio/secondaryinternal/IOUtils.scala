package com.pacbio.secondaryinternal

import java.nio.file.Path

import scala.io.Source

import com.pacbio.secondaryinternal.models.ServiceCondition


object IOUtils {

  def parseConditionCsv(path: Path): Seq[ServiceCondition] =
    parseConditionCsv(Source.fromFile(path.toFile))


  def parseConditionCsv(sx: Source): Seq[ServiceCondition] = {
    sx.getLines.drop(1).toSeq.map(x => parseLine(x.split(",").map(_.trim):_*))
  }

  def parseLine(args: String*): ServiceCondition = {
    parseLine(args(0), args(1), args(2).toInt)
  }

  def parseLine(condId : String, host : String, jobId : Int): ServiceCondition = {
    ServiceCondition(condId, host.split(":")(0), if (!host.contains(":")) 8081 else host.split(":")(1).toInt, jobId)

  }
}