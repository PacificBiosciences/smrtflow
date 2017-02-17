package com.pacbio.simulator

import java.util.UUID

import com.pacbio.common.models.UUIDJsonProtocol
import spray.json.DefaultJsonProtocol

/**
  * Created by amaster on 2/10/17.
  */
object ICSModel {

  case class RunObj(dataModel :String, uniqueId : UUID, summary : Option[String])
  case class ICSRun(startedby : String, run : RunObj)

}

trait ICSJsonProtocol extends DefaultJsonProtocol with UUIDJsonProtocol{
  import ICSModel._

  implicit val runObjF = jsonFormat3(RunObj)
  implicit val icsRunF = jsonFormat2(ICSRun)
}

object ICSJsonProtocol extends ICSJsonProtocol
