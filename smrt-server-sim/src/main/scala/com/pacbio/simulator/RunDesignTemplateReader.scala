package com.pacbio.simulator

/**
  * Created by amaster on 2/13/17.
  */
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.{Calendar, UUID}

import com.pacbio.secondary.smrtlink.io.XmlTemplateReader

import scala.xml.Node

class RunDesignTemplateReader(xmlFile: Path) {
  private def randomId(): UUID = UUID.randomUUID()
  private def randomContextId(): String = toRandomMovieContextId("SIM")
  private var subreadSetUuid: Option[UUID] = None

  def toRandomMovieContextId(instrument: String) = {
    val sx = new SimpleDateFormat("yyMMdd_HHmmss")
      .format(Calendar.getInstance().getTime)
    s"m${instrument}_$sx"
  }

  private val getSubreadsetUuid = {
    subreadSetUuid = Some(randomId())
    subreadSetUuid.get
  }

  def read: Node =
    XmlTemplateReader
      .fromFile(xmlFile)
      .globally()
      .substitute("{RUN_ID}", randomId())
      .perInstance()
      .substitute("{STATUS}", "Ready")
      //.perInstance()         .substitute("{movieLengthInMins}", movieLenInMins())
      .perNode("SubreadSet")
      .substituteMap {
        val collectionContextId = randomContextId()
        Map(
          "{SUBREAD_ID}" -> (() => getSubreadsetUuid),
          "{EXTERNAL_RESOURCE_ID}" -> (() => randomId())
          //"{collectionContextId}" -> (() => collectionContextId),
          //"{collectionPathUri}"   -> (() => s"//pbi/collections/xfer-test/$collectionContextId/1_A01/$runId/")
          //"{movieLengthInMins}"   -> (() => movieLengthInMins)
        )
      }
      .result()

  def readStr = read.mkString

  def readRundesignTemplateInfo =
    RunDesignTemplateInfo(readStr, subreadSetUuid.get)
}
