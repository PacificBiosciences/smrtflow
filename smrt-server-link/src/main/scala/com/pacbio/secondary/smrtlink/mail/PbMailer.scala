package com.pacbio.secondary.smrtlink.mail

import java.net.URL
import javax.mail.internet.InternetAddress

import courier._
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobTypeIds}
import com.pacbio.secondary.smrtlink.mail.Templates.{EmailJobFailedTemplate, EmailJobSuccessTemplate}
import com.typesafe.scalalogging.LazyLogging

import scala.util.control.NonFatal

/**
  * Created by mkocher on 7/21/17.
  */
trait PbMailer extends LazyLogging{
  import Defaults._

  private def sender(result: EmailTemplateResult, toAddress: InternetAddress) = {
    val mailer = Mailer()
    val fromAddress = new InternetAddress("noreply@pacb.com")

    val f = mailer(Envelope.from(fromAddress)
        .to(toAddress)
        .subject(result.subject)
        .content(Multipart().html(result.html)))

    f.onSuccess { case _ => logger.info(s"Successfully Sent Email $toAddress")}
    f.onFailure { case NonFatal(ex) => logger.error(s"Failed to send Email Error ${ex.getMessage}")}

    f
  }

  /**
    * Send email (if possible)
    *
    * @param job Engine Job
    * @param baseJobUrl Base Job URL Example: https://smrtlink-bihourly.nanofluidics.com:8243/sl/#/analysis/jobs
    */
  def sendEmail(job: EngineJob, baseJobUrl: Option[URL]): Unit = {

    Tuple3(job.createdByEmail, job.jobTypeId, baseJobUrl) match {
      case Tuple3(Some(email), JobTypeIds.PBSMRTPIPE.id, Some(baseUrl)) =>
        val emailInput = SmrtLinkEmail(email, job.id, job.name, job.createdAt.toString(), job.updatedAt.toString())
        val toAddress = new InternetAddress(email)
        val result = if (job.isSuccessful) EmailJobSuccessTemplate(emailInput) else EmailJobFailedTemplate(emailInput)
        sender(result, toAddress)
      case _ =>
        val msg = s"Unable to send email. BaseUrl:$baseJobUrl Address:${job.createdByEmail} JobType:${job.jobTypeId} JobId: ${job.id} state: ${job.state}"
        logger.debug(msg)
    }
  }
}
