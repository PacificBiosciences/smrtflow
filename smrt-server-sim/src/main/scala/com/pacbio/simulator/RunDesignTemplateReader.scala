package com.pacbio.simulator

/**
  * Created by amaster on 2/13/17.
  */

import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.{Calendar, UUID}

import com.pacbio.common.models.{XmlTemplateReader => GenTemplateReader}

import scala.xml.{Node, XML}

class RunDesignTemplateReader(xmlFile: Path) {
  private def randomId(): UUID = UUID.randomUUID()
  private def randomContextId(): String = toRandomMovieContextId("SIM")

  /*var count = 0
  val runId = randomId()
  val runName = s"Sim-Run-$runId"
  val movieLengthInMins = numFrames / (60.0 * 80.0)*/

  def toRandomMovieContextId(instrument:String) = {
    val sx = new SimpleDateFormat("yyMMdd_HHmmss").format(Calendar.getInstance().getTime)
    s"m${instrument}_$sx"
  }

  def read :Node =
    GenTemplateReader
      .fromFile(xmlFile)
      .globally()            .substitute("{RUN_ID}", randomId())
      .perInstance()         .substitute("{STATUS}", "READY")
      //.perInstance()         .substitute("{movieLengthInMins}", movieLenInMins())
      .perNode("SubreadSet") .substituteMap {
      val collectionContextId = randomContextId()
      Map(
        "{SUBREAD_ID}"           -> (() => randomId())
        //"{collectionContextId}" -> (() => collectionContextId),
        //"{collectionPathUri}"   -> (() => s"//pbi/collections/xfer-test/$collectionContextId/1_A01/$runId/")
        //"{movieLengthInMins}"   -> (() => movieLengthInMins)
      )
    }.result()

  def readStr = read.mkString
}
