package com.pacbio.secondary.smrtlink

import java.net.URL

/**
  * Created by mkocher on 7/21/17.
  */
package object mail {

  case class SmrtLinkEmail(emailAddress: String, jobId: Int, jobName: String, createdAt: String, completedAt: String, jobURL: URL)
  case class EmailTemplateResult(subject: String, html: String)

}
