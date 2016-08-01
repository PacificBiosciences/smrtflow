package com.pacbio.simulator.steps

import java.util.UUID

import com.pacbio.simulator.Scenario
import spray.json._

trait RunDesignSteps {
  this: Scenario with VarSteps with HttpSteps =>

  private def runPath(runId: Var[UUID]): Var[String] = runId.mapWith(u => s"/smrt-link/runs/$u")

  object CreateRunDesign {
    private def xmlToJson(xml: String): String = {
      val xmlValue: JsValue = JsString(xml)
      val jsonObject: JsObject = JsObject(("dataModel", xmlValue))
      jsonObject.compactPrint
    }

    def apply(runXml: Var[String]): HttpStep = HttpStep
      .post(Var("/smrt-link/runs"), runXml.mapWith(xmlToJson))
      .header("Content-Type", "application/json")
      .header("Charset", "UTF-8")
  }

  object GetRunDesign {
    def apply(uuid: Var[UUID]): HttpStep = HttpStep.get(runPath(uuid))
  }

  object DeleteRunDesign {
    def apply(uuid: Var[UUID]): HttpStep = HttpStep.delete(runPath(uuid))
  }
}
