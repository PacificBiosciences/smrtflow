package com.pacbio.secondary.smrtlink.mail

import java.net.URL
import javax.mail.internet
import javax.mail.internet.InternetAddress

import com.pacbio.secondary.smrtlink.actors.JobsDao

import scala.concurrent.Future
import courier._
import com.typesafe.scalalogging.LazyLogging
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobTypeIds
}
import com.pacbio.secondary.smrtlink.mail.MailTemplates.{
  EmailCoreJobFailedTemplate,
  EmailCoreJobSuccessTemplate,
  EmailMultiJobFailedTemplate,
  EmailMultiJobSuccessTemplate
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.MailConfig
import spray.json._
import DefaultJsonProtocol._

import scala.util.Try

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

  val NOT_CONFIGURED_FOR_MAIL_MSG =
    "Skipping sending Email. System is NOT configured for Emailing."

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

  private def toCoreEmailInput(userEmail: String,
                               job: EngineJob,
                               jobsBaseUrl: URL): EmailTemplateResult = {
    // Note, because of the js "#" the URL or URI .resolve() doesn't work as expected.
    val jobIdUrl = new URL(jobsBaseUrl.toString() + s"/${job.id}")
    val toAddress = new InternetAddress(userEmail) // Don't use job.createdBy because it is Option[String]

    val emailInput = SmrtLinkCoreJobEmailInput(toAddress,
                                               job.id,
                                               job.name,
                                               job.state,
                                               job.createdAt,
                                               job.updatedAt,
                                               jobIdUrl,
                                               job.smrtlinkVersion)

    if (job.isSuccessful) EmailCoreJobSuccessTemplate(emailInput)
    else EmailCoreJobFailedTemplate(emailInput)
  }

  private def toNotEligibleEmailSummary(job: EngineJob) =
    s"Job ${job.id} type:${job.jobTypeId} state:${job.state} is NOT eligible for email sending. no createdByEmail user. Skipping sending Email"

  /**
    * Only Analysis Jobs and Multi-Jobs will send email.
    *
    */
  private def shouldSendEmailByJobType(
      jobTypeId: String,
      expectedJobType: JobTypeIds.JobType): Boolean = {
    JobTypeIds
      .fromString(jobTypeId)
      .map(jt => jt == expectedJobType)
      .getOrElse(false)
  }

  private def shouldSendCoreJobEmail(job: EngineJob): Boolean =
    job.isComplete && shouldSendEmailByJobType(job.jobTypeId,
                                               JobTypeIds.PBSMRTPIPE)

  // These are the fundamental interfaces to be used by the ServiceXRunners
  def sendCoreJobEmail(job: EngineJob,
                       baseJobsUrl: URL,
                       mailConfig: MailConfig): Future[String] = {

    job.createdByEmail match {
      case Some(email) if shouldSendCoreJobEmail(job) =>
        val emailTemplateResult = toCoreEmailInput(email, job, baseJobsUrl)
        val toAddress = new InternetAddress(email)
        sender(emailTemplateResult, toAddress, toAddress, mailConfig)
      case _ =>
        Future.successful(toNotEligibleEmailSummary(job))
    }
  }

  private def toMultiEmailInput(
      userEmail: String,
      job: EngineJob,
      jobsBaseUrl: URL,
      childrenJobs: Seq[EngineJob]): EmailTemplateResult = {
    // Note, because of the js "#" the URL or URI .resolve() doesn't work as expected.
    val jobIdUrl = new URL(jobsBaseUrl.toString() + s"/${job.id}")
    val toAddress = new InternetAddress(userEmail) // Don't use job.createdBy because it is Option[String]

    val emailInput = SmrtLinkMultiJobEmailInput(toAddress,
                                                job.id,
                                                job.name,
                                                job.state,
                                                job.createdAt,
                                                job.updatedAt,
                                                jobIdUrl,
                                                job.smrtlinkVersion,
                                                childrenJobs)

    if (job.isSuccessful) EmailMultiJobSuccessTemplate(emailInput)
    else EmailMultiJobFailedTemplate(emailInput)
  }

  private def shouldSendMultiCoreJob(job: EngineJob): Boolean =
    job.isComplete && shouldSendEmailByJobType(job.jobTypeId,
                                               JobTypeIds.MJOB_MULTI_ANALYSIS)

  def loadAndSubmitEmail(updatedJob: EngineJob,
                         dao: JobsDao,
                         userEmail: String,
                         mailConfig: MailConfig,
                         jobsBaseUrl: URL): Future[String] = {
    for {
      childrenJobIds <- Future.fromTry(
        Try(
          updatedJob.workflow.parseJson
            .convertTo[Seq[Int]])) // This needs to be improved
      childrenJobs <- Future.sequence(
        childrenJobIds.map(i => dao.getJobById(i)))
      emailInput <- Future.successful(
        toMultiEmailInput(userEmail, updatedJob, jobsBaseUrl, childrenJobs))
      msg <- sender(emailInput,
                    new InternetAddress(userEmail),
                    mailConfig = mailConfig)
    } yield msg
  }

  // Determine if Email Should be sent from the Config parameters
  def determineAndSendEmail(job: EngineJob,
                            dao: JobsDao,
                            mailConfig: MailConfig,
                            jobsBaseUrl: URL): Future[String] = {
    if (shouldSendMultiCoreJob(job)) {
      job.createdByEmail
        .map(email =>
          loadAndSubmitEmail(job, dao, email, mailConfig, jobsBaseUrl))
        .getOrElse(Future.successful(toNotEligibleEmailSummary(job)))
    } else {
      Future.successful(toNotEligibleEmailSummary(job))
    }
  }

  def sendMultiAnalysisJobMail(jobId: Int,
                               dao: JobsDao,
                               mailConfig: MailConfig,
                               jobsBaseUrl: URL): Future[String] = {
    for {
      updatedJob <- dao.getJobById(jobId)
      msg <- determineAndSendEmail(updatedJob, dao, mailConfig, jobsBaseUrl)
    } yield msg
  }

}
