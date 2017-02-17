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
import com.pacbio.simulator.ICSModel.ICSRun


/**
  * Client to Instrument Control
  */
class InstrumentControlClient(val baseUrl: URL)(implicit actorSystem: ActorSystem) extends ICSJsonProtocol{

  //import com.pacbio.simulator.ICSJsonProtocol

  // Context to run futures in
  implicit val executionContext = actorSystem.dispatcher

  private def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString

  val mapToJson: HttpResponse => HttpResponse = { response =>
    response.withEntity(HttpEntity(ContentTypes.`application/json`, response.entity.data))
  }

  def runStatusPipeline = sendReceive ~> mapToJson
  //def alarmStatusPipeline= sendReceive ~> mapToJson
  //def alarmStatusPipeline: HttpRequest => Future[JsValue] = sendReceive ~> unmarshal[JsValue]

  /*def postAlarms(alarms : JsObject) = alarmStatusPipeline {
    Post(toUrl("/alarms"), alarms)~> addHeader(`Content-Type`(`application/json`))
  }*/

  // todo - instead of json, retrieve the object
  def postRun(icsRun: ICSRun) = runStatusPipeline{
    Post(toUrl("/run"), icsRun)~> addHeader(`Content-Type`(`application/json`))
  }

  // todo - instead of json retrieve the object
  def postRunStart = runStatusPipeline{
    Post(toUrl("/run/start"))~> addHeader(`Content-Type`(`application/json`))
  }

  // todo - implement get method to query for run status
}

