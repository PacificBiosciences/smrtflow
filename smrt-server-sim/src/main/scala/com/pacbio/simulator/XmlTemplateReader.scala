package com.pacbio.simulator

import java.nio.file.Path
import java.util.UUID

import com.pacbio.common.models.{XmlTemplateReader => GenTemplateReader}
import com.pacbio.primary.io.IOUtils
import scala.xml.{XML, Node}

// TODO(smcclellan): Merge with XmlTemplateReader in smrt-server-base?
class XmlTemplateReader(xmlFile: Path, val numFrames: Int) {
  private def randomId(): UUID = UUID.randomUUID()
  private def randomContextId(): String = IOUtils.toRandomMovieContextId("SIM")

  val runId = randomId()
  val runName = s"Sim-Run-$runId"
  val movieLengthInMins = numFrames / (60.0 * 80.0)

  def read :Node =
    GenTemplateReader
      .fromFile(xmlFile)
      .globally()            .substitute("{runName}", runName)
      .perInstance()         .substitute("{randomId()}", randomId())
      .perNode("SubreadSet") .substituteMap {
      val collectionContextId = randomContextId()
      Map(
        "{subreadId}"           -> (() => randomId()),
        "{collectionContextId}" -> (() => collectionContextId),
        "{collectionPathUri}"   -> (() => s"//pbi/collections/xfer-test/$collectionContextId/1_A01/$runId/"),
        "{movieLengthInMins}"   -> (() => movieLengthInMins)
      )
    }.result()

  def readStr = read.mkString
