package com.pacbio.secondary.smrtlink

import java.net.URL
import javax.mail.internet.InternetAddress

import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.EngineJob
import org.joda.time.{DateTime => JodaDateTime}

/**
  * Created by mkocher on 7/21/17.
  */
package object mail {

  case class EmailTemplateResult(subject: String, html: String)

  trait SmrtLinkEmailInputs {
    val emailAddress: InternetAddress
    val jobId: Int
    val jobName: String
    val jobState: AnalysisJobStates.JobStates
    val createdAt: JodaDateTime
    val completedAt: JodaDateTime
    val jobURL: URL
    val smrtLinkVersion: Option[String]
    def wasSuccessful() = jobState == AnalysisJobStates.SUCCESSFUL
  }

  case class SmrtLinkCoreJobEmailInput(emailAddress: InternetAddress,
                                       jobId: Int,
                                       jobName: String,
                                       jobState: AnalysisJobStates.JobStates,
                                       createdAt: JodaDateTime,
                                       completedAt: JodaDateTime,
                                       jobURL: URL,
                                       smrtLinkVersion: Option[String] = None)
      extends SmrtLinkEmailInputs

  case class SmrtLinkMultiJobEmailInput(emailAddress: InternetAddress,
                                        jobId: Int,
                                        jobName: String,
                                        jobState: AnalysisJobStates.JobStates,
                                        createdAt: JodaDateTime,
                                        completedAt: JodaDateTime,
                                        jobURL: URL,
                                        smrtLinkVersion: Option[String] = None,
                                        childrenJobs: Seq[EngineJob])
      extends SmrtLinkEmailInputs

}
