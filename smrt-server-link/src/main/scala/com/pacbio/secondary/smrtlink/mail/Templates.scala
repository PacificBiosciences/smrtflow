package com.pacbio.secondary.smrtlink.mail

import scalatags.Text.all._

/**
  * Created by mkocher on 7/21/17.
  */
object Templates {

  trait EmailTemplate[T] {
    def apply(input: T): EmailTemplateResult
  }


  object EmailJobSuccessTemplate extends EmailTemplate[SmrtLinkEmail] {
    def apply(input: SmrtLinkEmail) = {
      val html = s"""
         |Dear ${input.emailAddress},
         |
         |Your analysis job has successfully completed.
         |
         |Job ID: ${input.jobId}
         |Job name: ${input.jobName}
         |Start time: ${input.createdAt}
         |Finish time: ${input.completedAt}
         |
         |Please visit the following link to view the results: [${input.jobURL}]
         |Powered by SMRT Link
         |Pacific Biosciences of California, Inc.
         |
      """.stripMargin

      EmailTemplateResult(s"SMRT Link Job ${input.jobId} Successfully Completed: ${input.jobName}", html)
    }
  }

  object EmailJobFailedTemplate extends EmailTemplate[SmrtLinkEmail] {
    def apply(input: SmrtLinkEmail) = {
      val html =
        s"""
         |Dear ${input.emailAddress},
         |
         |Your analysis job has Failed.
         |
         |Job ID: ${input.jobId}
         |Job name: ${input.jobName}
         |Start time: ${input.createdAt}
         |Finish time: ${input.completedAt}
         |
         |Please visit the following link to view the results: [${input.jobURL}]
         |Powered by SMRT Link
         |Pacific Biosciences of California, Inc.
         |

    """.stripMargin
    EmailTemplateResult(s"SMRT Link Job ${input.jobId} Failed ${input.jobName}", html)
    }
  }

}
