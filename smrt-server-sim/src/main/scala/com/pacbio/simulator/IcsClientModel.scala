package com.pacbio.simulator

import java.util.UUID

import com.pacbio.common.models.UUIDJsonProtocol
import spray.json.DefaultJsonProtocol

/**
  * Created by amaster on 2/10/17.
  */
object ICSModel {

  case class RunObj(dataModel :String, uniqueId : UUID, summary : String)
  case class ICSRun(startedby : String, run : RunObj)
  //case class PostRunResponse(title:String)
  case class RunResponse(createdAt : Option[String]=null,
                        createdBy : Option[String]=null,
                        dataModel: String,
                        instrumentSerialNumber : Option[String]=null,
                        name : String,
                        reserved : Boolean,
                        status: Int,
                        summary:String,
                        totalCells : Int,
                        uniqueId : Option[String] = null)

 /* case class Compatibility (description : Option[String]=null,
                            name : Option[String]=null,
                            partNumber : String)

  case class ExpectedPartNumbers(allocatedQuantity : Int,
                                 description: String,
                                 name :String,
                                 partNumber: String,
                                 requiredQuantity : Int)

  case class ConsumableState(assigned: Boolean=false,
                             cellCount : Int = 0,
                             problemInfo : Option[String]=null)

  case class Consumables(expirationDate : Option[String]=null,
                         lotId: Option[String]=null,
                         moduleNumber:Int,
                         name:Option[String],
                         partNumber:Option[String]=null,
                         serialNumber :Option[String]=null,
                         state: ConsumableState)

  case class Stations(consumable: Seq[Consumables],
                      name : String,
                      occupied :Boolean)

  case class CellRequirements (compatibility: Seq[Compatibility],
                               expectedPartNumbers: Seq[ExpectedPartNumbers],
                               itemsNeeded :Int,
                               stations : Seq[Stations])

  case class ReqManagement(allowExpiredInventory : Boolean,
                           allowPartNumberMismatch :Boolean,
                           cellRequirements : CellRequirements )*/
}

trait ICSJsonProtocol extends DefaultJsonProtocol with UUIDJsonProtocol{
  import ICSModel._

  implicit val runObjF = jsonFormat3(RunObj)
  implicit val icsRunF = jsonFormat2(ICSRun)
  implicit val icsRunGetF = jsonFormat10(RunResponse)
  //implicit val postRunResponseF = jsonFormat1(PostRunResponse)
}

object ICSJsonProtocol extends ICSJsonProtocol
