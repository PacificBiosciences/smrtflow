package com.pacbio.secondary.smrtlink.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.util.Timeout
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.common.services._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.loaders.PacBioAutomationConstraintsLoader

import scala.concurrent._


/*
This is kinda a mess. The loaders are in
com.pacbio.secondary.smrtserver.loaders in smrt-server-analysis subproject.
This would require a large shifting around of models. Putting this
here for now
 */
class AutomationConstraintsDao(data: JsValue) {

  def getAutomationConstraints: Future[JsValue] = Future { data }

  def updateAutomationConstraints(update: JsValue) = Future {update}
}

/**
  * Created by mkocher on 2/6/17.
  */
class AutomationConstraintService(dao: AutomationConstraintsDao) extends SmrtLinkBaseMicroService {

  import SmrtLinkJsonProtocols._

  val ROUTE_PREFIX = "automation-constraints"

  val manifest = PacBioComponentManifest(
    toServiceId("automation_constraint"),
    "Automation Constraint Service",
    "0.1.0", "Automation Constraint Service for PartNumbers and Compatibility Matrix")

  val routes = {
    pathPrefix(ROUTE_PREFIX) {
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              dao.getAutomationConstraints
            }
          }
        }
      }
    }
  }

}

trait AutomationConstraintServiceProvider extends PacBioAutomationConstraintsLoader{
  this: SmrtLinkConfigProvider with ServiceComposer =>

  val automationConstraintService: Singleton[AutomationConstraintService] =
    Singleton(() => new AutomationConstraintService(new AutomationConstraintsDao(pacBioAutomationToJson(pacBioAutomationConstraints()))))

  addService(automationConstraintService)
}