package com.pacbio.secondary.smrtlink.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.actors.ActorSystemProvider
import com.pacbio.secondary.analysis.engine.CommonMessages._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.common.services._

import scala.concurrent._


/*
This is kinda a mess. The loaders are in
com.pacbio.secondary.smrtserver.loaders in smrt-server-analysis subproject.
This would require a large shifting around of models. Putting this
here for now
 */
class AutomationConstraintsDao(data: JsObject) {

  def getAutomationConstraints: Future[JsObject] = Future { data }

  def updateAutomationConstraints(update: JsObject) = Future {update}
}

/**
  * Created by mkocher on 2/6/17.
  */
class AutomationConstraintService(dao: AutomationConstraintsDao) extends BaseSmrtService with JobServiceConstants{

  import SmrtLinkJsonProtocols._

  val ROUTE_PREFIX = "automation-constraints"

  val manifest = PacBioComponentManifest(
    toServiceId("automation_constraint"),
    "Automation Constraint Service",
    "0.1.0", "Automation Constraint Service for PartNumbers and Compatibility Matrix")

  implicit val timeout = Timeout(30.seconds)

  val routes = {
    path(ROUTE_PREFIX) {
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

trait AutomationConstraintServiceProvider {
  this: ServiceComposer =>

  val automationConstraintService: Singleton[AutomationConstraintService] =
    Singleton(() => new AutomationConstraintService(new AutomationConstraintsDao(JsObject.empty)))

  addService(automationConstraintService)
}