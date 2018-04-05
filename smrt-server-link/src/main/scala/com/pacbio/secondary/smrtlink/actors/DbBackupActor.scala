package com.pacbio.secondary.smrtlink.actors

import java.util.UUID

import spray.json._
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.DbBackupActor.SubmitDbBackUpJob
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jobtypes.DbBackUpJobOptions
import com.pacbio.secondary.smrtlink.jsonprotocols.ServiceJobTypeJsonProtocols
import com.typesafe.scalalogging.LazyLogging

import concurrent.duration._
import concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object DbBackupActor {
  case class SubmitDbBackUpJob(user: String, jobName: String, comment: String)
}

class DbBackupActor(dao: JobsDao) extends Actor with LazyLogging {

  import DefaultJsonProtocol._
  import ServiceJobTypeJsonProtocols._

  implicit val timeout = Timeout(20.seconds)

  override def receive: Receive = {
    case SubmitDbBackUpJob(user, jobName, comment) =>
      val desc = "System created Database backup"
      val opts = DbBackUpJobOptions(user,
                                    s"$jobName",
                                    Some(jobName),
                                    description = None,
                                    projectId =
                                      Some(JobConstants.GENERAL_PROJECT_ID))
      val jopts: JsObject = opts.toJson.asJsObject

      def fx: Future[MessageResponse] =
        dao
          .createCoreJob(UUID.randomUUID(),
                         jobName,
                         comment,
                         opts.jobTypeId,
                         jsonSetting = jopts,
                         submitJob = true)
          .map(j =>
            MessageResponse(s"Created DB Backup job id:${j.id} ${j.uuid}"))

      fx pipeTo sender
  }

}

trait DbBackupActorProvider {
  this: ActorRefFactoryProvider
    with JobsDaoProvider
    with SmrtLinkConfigProvider =>

  val dbBackupActor: Singleton[ActorRef] =
    Singleton(
      () => actorRefFactory().actorOf(Props(classOf[DbBackupActor], jobsDao()))
    )
}
