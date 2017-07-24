package com.pacbio.secondary.smrtlink

import java.net.URL
import javax.mail.internet.InternetAddress

import org.joda.time.{DateTime => JodaDateTime}

/**
  * Created by mkocher on 7/21/17.
  */
package object mail {

  case class SmrtLinkEmailInput(emailAddress: InternetAddress, jobId: Int, jobName: String, createdAt: JodaDateTime, completedAt: JodaDateTime, jobURL: URL, smrtLinkVersion: Option[String] = None)
  case class EmailTemplateResult(subject: String, html: String)

}
