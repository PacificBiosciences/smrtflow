package com.pacbio.secondary.smrtlink.mail

import java.net.URL
import javax.mail.internet.InternetAddress

import scala.util.control.NonFatal
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import courier._
import com.typesafe.scalalogging.LazyLogging
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobTypeIds
}
import com.pacbio.secondary.smrtlink.mail.MailTemplates.{
  EmailJobFailedTemplate,
  EmailJobSuccessTemplate
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.MailConfig

/**
  *
  * This is mixing up a bet of template generation and data munging.
  *
  * The two core methods for sending the emails for the "Core" and "Multi" analysis
  * job types should be defined here.
  *
  * This is mixing up a bet of template generation and data munging. This might make
  * more sense to push to new location.
  *
  * Created by mkocher on 7/21/17.
  */
trait PbMailer extends LazyLogging {
  import Defaults._

  val DEFAULT_FROM = new InternetAddress("noreply@pacb.com")

  /**
    * Thin layer around the courier lib to send an email
    *
    * @param result      Email Template result
    * @param toAddress   To Address
    * @param fromAddress From Address
    * @return
    */
  def sender(result: EmailTemplateResult,
             toAddress: InternetAddress,
             fromAddress: InternetAddress = DEFAULT_FROM,
             mailConfig: MailConfig): Future[String] = {
    val mailer = {
      val m = Mailer(mailConfig.host, mailConfig.port)
      mailConfig.user
        .flatMap { user =>
          mailConfig.password.map { pw =>
            m.auth(true).as(user, pw)()
          }
        }
        .getOrElse(m())
    }

    val f = mailer(
      Envelope
        .from(fromAddress)
        .to(toAddress)
        .subject(result.subject)
        .content(Multipart().html(result.html)))

    f.map(_ => s"Successfully sent email to $toAddress")
  }

  private def toEmailInput(userEmail: String,
                           job: EngineJob,
                           jobsBaseUrl: URL): EmailTemplateResult = {
    // Note, because of the js "#" the URL or URI .resolve() doesn't work as expected.
    val jobIdUrl = new URL(jobsBaseUrl.toString() + s"/${job.id}")
    val toAddress = new InternetAddress(userEmail) // Don't use job.createdBy because it is Option[String]

    val emailInput = SmrtLinkEmailInput(toAddress,
                                        job.id,
                                        job.name,
                                        job.state,
                                        job.createdAt,
                                        job.updatedAt,
                                        jobIdUrl,
                                        job.smrtlinkVersion)

    if (job.isSuccessful) EmailJobSuccessTemplate(emailInput)
    else EmailJobFailedTemplate(emailInput)
  }

  /**
    * Only Analysis Jobs and Multi-Jobs will send email.
    *
    */
  private def shouldSendCoreEmailByJobType(jobTypeId: String): Boolean = {
    JobTypeIds.fromString(jobTypeId).exists { x: JobTypeIds.JobType =>
      x match {
        case JobTypeIds.PBSMRTPIPE => true
        case _ => false
      }
    }
  }

  private def shouldSendCoreJobEmail(job: EngineJob): Boolean =
    job.isComplete && shouldSendCoreEmailByJobType(job.jobTypeId)

  // These are the fundamental interfaces to be used by the ServiceXRunners
  def sendCoreJobEmail(job: EngineJob,
                       baseJobsUrl: URL,
                       mailConfig: MailConfig): Future[String] = {

    job.createdByEmail match {
      case Some(email) if shouldSendCoreJobEmail(job) =>
        val emailTemplateResult = toEmailInput(email, job, baseJobsUrl)
        val toAddress = new InternetAddress(email)
        sender(emailTemplateResult, toAddress, toAddress, mailConfig)
      case _ =>
        Future.successful(
          s"Job ${job.id} type:${job.jobTypeId} state:${job.state} is NOT eligible for email sending. no createdByEmail user. Skipping sending Email")
    }
  }
}
