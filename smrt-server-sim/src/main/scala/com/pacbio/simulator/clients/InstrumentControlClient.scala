package com.pacbio.simulator.clients

/**
  * Created by amaster on 2/10/17.
  */

import java.net.URL

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.http.HttpHeaders._
import MediaTypes._
import com.pacbio.simulator.ICSJsonProtocol
import com.pacbio.simulator.ICSModel.{ICSRun, RunResponse}
import spray.json._

import scala.concurrent.Future

// not sure if this will be useful
object ICSState {

  sealed trait State {
    val ind: Int
    val name: String
  }

  object Ready  extends State{val ind = 0; val name = "Ready"}
  object Idle  extends State{val ind = 1; val name ="Idle"}
  object SystemTest  extends State{val ind =2; val name ="SystemTest"}
  object Starting  extends State{val ind =3; val name ="Starting"}
  object Running  extends State{val ind =4; val name ="Running"}
  object Aborting  extends State{val ind =5; val name ="Aborting"}
  object Aborted  extends State{val ind =6; val name ="Aborted"}
  object Terminated  extends State{val ind =7; val name ="Terminated"}
  object Completing  extends State{val ind =8;val name ="Completing"}
  object Complete  extends State{val ind =9;val name = "Complete"}
  object Paused  extends State{val ind =10;val name = "Paused"}
  object Unknown  extends State{val ind =11; val name ="Unknown"}

  final val ALL = Set(Ready, Idle, SystemTest,Starting,Running,Aborting, Aborted,Terminated,Completing,Complete,Paused,Unknown)
  def fromIndex(ind : Int) : Option[State] = ALL.map(xx => (xx.ind,xx)).toMap.get(ind)
}

object ICSUris{
  final val RUN_URI = "/run"
  final val RUN_START_URI = "/run/start"
  final val RUN_RQMTS_URI="/run/rqmts"
  final val LOAD_INVENTORY_URI = "/test/endtoend/inventory"
}
/**
  * Client to Instrument Control
  */
class InstrumentControlClient(val baseUrl: URL)(implicit actorSystem: ActorSystem) extends ICSJsonProtocol{

  //import com.pacbio.simulator.ICSJsonProtocol
  import ICSUris._
  // Context to run futures in
  implicit val executionContext = actorSystem.dispatcher

  private def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString

  val mapToJson: HttpResponse => HttpResponse = { response =>
    response.withEntity(HttpEntity(ContentTypes.`application/json`, response.entity.data))
  }

  def runPostPipeline = sendReceive ~> mapToJson

  def runGetPipeline = runPostPipeline

  def unmarshalJSON(httpResponse: HttpResponse): JsValue = {
    val aa = httpResponse.entity.asString.parseJson
    println(s"unmarshalled json : $aa")
    aa
  }

  val mapToJsValuePipeline  =  sendReceive ~> unmarshalJSON

  def runStatusPipeline : HttpRequest => Future[RunResponse] = sendReceive ~> unmarshal[RunResponse]

  // POST /run  NOTE : cannot retrieve object, json contains an element with name "type"
  def postRun(icsRun: ICSRun) = runPostPipeline{
    //println(s"running post : $icsRun")
    Post(toUrl(RUN_URI), icsRun)~> addHeader(`Content-Type`(`application/json`))
  }

  // POST /run/start
  def postRunStart = runPostPipeline{
    Post(toUrl(RUN_START_URI))~> addHeader(`Content-Type`(`application/json`))
  }

  // POST /run/rqmts
  def postRunRqmts = runPostPipeline{
    Post(toUrl(RUN_RQMTS_URI))~> addHeader(`Content-Type`(`application/json`))
  }

  /*
  reads response as json, do not deserialize that objects, its too huge and unncessary for accessing
  just one elemnent from json
   */
  // GET /run/rqmts
  def getRunRqmts = mapToJsValuePipeline{
    Get(toUrl("/run/rqmts"))
  }

  // GET /run
  def getRunStatus = runStatusPipeline{
    Get(toUrl(RUN_URI)) ~> addHeader(`Content-Type`(`application/json`))
  }

  // POST /test/endtoend/inventory
  /*
  instead of running the python script discretely, Manny created an endpt in ICS to load the run.
  this is a fire and forget request
   */
  def postLoadInventory = runPostPipeline{
    Post(toUrl(LOAD_INVENTORY_URI))~> addHeader(`Content-Type`(`application/json`))
  }

}

