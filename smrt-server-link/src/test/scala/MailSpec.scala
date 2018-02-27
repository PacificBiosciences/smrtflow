import java.net.URL
import java.util.UUID
import javax.mail.internet.InternetAddress

import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobTypeIds
}
import com.pacbio.secondary.smrtlink.mail.{
  SmrtLinkCoreJobEmailInput,
  SmrtLinkMultiJobEmailInput
}
import com.pacbio.secondary.smrtlink.mail.MailTemplates.{
  EmailCoreJobFailedTemplate,
  EmailCoreJobSuccessTemplate,
  EmailMultiJobSuccessTemplate,
  EmailMultiJobFailedTemplate
}
import org.specs2.mutable.Specification
import org.joda.time.{DateTime => JodaDateTime}

class MailSpec extends Specification {

  val jobId = 1234
  val jobUrl = new URL(s"http://my-domain:8454/#/analysis/job/$jobId")
  val createdAt = JodaDateTime.now()
  val createdBy = "example-user"
  val smrtLinkVersion = Some("9.9.9")
  val emailAddress = new InternetAddress("user@mail.com")
  val emailInput = SmrtLinkCoreJobEmailInput(emailAddress,
                                             jobId,
                                             "Job-Name",
                                             AnalysisJobStates.SUCCESSFUL,
                                             createdAt,
                                             createdAt,
                                             jobUrl,
                                             smrtLinkVersion)

  def toEngineJob(i: Int) =
    EngineJob(
      i,
      UUID.randomUUID(),
      s"Child-job-$i",
      "",
      createdAt,
      createdAt,
      createdAt,
      AnalysisJobStates.SUCCESSFUL,
      JobTypeIds.PBSMRTPIPE.id,
      "/path",
      "{}",
      None,
      Some(createdBy),
      smrtLinkVersion
    )

  val childrenJobs = (0 until 2).map(toEngineJob)

  // Create a mixed failed and successful job list
  val failedChildrenJobs: Seq[EngineJob] =
    (0 until 2)
      .map(toEngineJob)
      .map(_.copy(state = AnalysisJobStates.FAILED)) ++ childrenJobs

  val emailMultiInput = SmrtLinkMultiJobEmailInput(
    emailAddress,
    jobId,
    "Job-Name",
    AnalysisJobStates.SUCCESSFUL,
    createdAt,
    createdAt,
    jobUrl,
    smrtLinkVersion,
    childrenJobs
  )

  val emailFailedMultiInput =
    emailMultiInput.copy(jobState = AnalysisJobStates.FAILED,
                         childrenJobs = failedChildrenJobs)

  "Mail Template Rendering Smoke test" should {
    "render successful template" in {
      val templateResult = EmailCoreJobSuccessTemplate(emailInput)
      //println(templateResult.html)
      true must beTrue
    }
    "render failed template" in {
      val templateResult = EmailCoreJobFailedTemplate(
        emailInput.copy(jobState = AnalysisJobStates.FAILED))
      //println(templateResult.html)
      true must beTrue
    }
  }
  "Mail MultiAnalysis Rendering Smoke Test" should {
    "should render successful template" in {
      val t = EmailMultiJobSuccessTemplate(emailMultiInput)
      //println(t.html)
      true must beTrue
    }
    "should render successful template" in {
      val t = EmailMultiJobFailedTemplate(emailFailedMultiInput)
      //println("")
      //println(t.html)
      true must beTrue
    }
  }
}
