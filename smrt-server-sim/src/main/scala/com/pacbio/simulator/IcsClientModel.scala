package com.pacbio.simulator

import java.util.UUID

import com.pacbio.common.models.UUIDJsonProtocol
import spray.json.DefaultJsonProtocol

/**
  * Created by amaster on 2/10/17.
  */
object ICSModel {

  case class RunObj(dataModel: String, uniqueId: UUID, summary: String)
  case class ICSRun(startedby: String, run: RunObj)

  case class RunResponse(createdAt: Option[String] = null,
                         createdBy: Option[String] = null,
                         dataModel: String,
                         instrumentSerialNumber: Option[String] = null,
                         name: String,
                         reserved: Boolean,
                         status: Int,
                         summary: String,
                         totalCells: Int,
                         uniqueId: Option[String] = null)

  //NOTE : this is just a subset of InstrumentState elements. We do not need to deserialize the entire json
  case class InstrumentState(runState: Int, state: Int)

  // NOTE : this is just a subset of RunRequirements elements. We do not need to deserialize the entire json
  case class RunRequirements(hasSufficientInventory: Boolean,
                             hasValidDataTransferLocations: Boolean)
}

trait ICSJsonProtocol extends DefaultJsonProtocol with UUIDJsonProtocol {
  import ICSModel._

  implicit val runObjF = jsonFormat3(RunObj)
  implicit val icsRunF = jsonFormat2(ICSRun)
  implicit val icsRunGetF = jsonFormat10(RunResponse)
  implicit val instrumentStateF = jsonFormat2(InstrumentState)
  implicit val runRequirementsFF = jsonFormat2(RunRequirements)
}

object ICSJsonProtocol extends ICSJsonProtocol
