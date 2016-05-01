package com.pacbio.secondary.analysis.engine.actors

import akka.actor.{Actor, ActorLogging}
import akka.pattern.ask
import com.pacbio.secondary.analysis.engine.CommonMessages.FailedMessage
import com.pacbio.secondary.analysis.pipelines.PipelineTemplateDao

/**
 * Actor for interacting with pipeline templates and presets
 *
 * Created by mkocher on 6/24/15.
 */
object PipelineTemplateDaoActor {

  // Pipeline Templates And Presets
  abstract class PipelineTemplateMessage

  case object GetAllPipelineTemplates extends PipelineTemplateMessage

  case class GetPipelineTemplateById(pipelineTemplateId: String) extends PipelineTemplateMessage

  case class GetPipelinePresetsByPipelineId(pipelineTemplateId: String) extends PipelineTemplateMessage

  case class GetPipelinePresetBy(pipelineTemplateId: String, presetId: String) extends PipelineTemplateMessage

}


class PipelineTemplateDaoActor(dao: PipelineTemplateDao) extends Actor with ActorLogging{
  import PipelineTemplateDaoActor._

  def receive = {
    // Pipeline Templates
    case GetAllPipelineTemplates =>
      sender ! dao.getPipelineTemplates

    case GetPipelineTemplateById(n: String) =>
      dao.getPipelineTemplateById(n) match {
        case Some(x) => sender ! Right(x)
        case _ => sender ! Left(FailedMessage(s"Unable to find pipeline template '$n'"))
      }
    case GetPipelinePresetBy(templateId: String, presetId: String) =>
      dao.getPipelinePresetBy(templateId, presetId) match {
        case Some(x) => sender ! Right(x)
        case _ => sender ! Left(FailedMessage(s"Unable to find pipeline template preset '$presetId' for '$templateId'"))
      }
    case GetPipelinePresetsByPipelineId(presetId: String) =>
      dao.getPresetsFromPipelineTemplateId(presetId) match {
        case Some(x) => sender ! Right(x)
        case _ => sender ! Left(FailedMessage(s"Unable to find pipeline template id '$presetId'. No presets found."))
      }
    case x => log.debug(s"Unhandled message $x to Pipeline template Actor")
  }
}
