import java.net.URL
import javax.mail.internet.InternetAddress

import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.mail.SmrtLinkEmailInput
import com.pacbio.secondary.smrtlink.mail.MailTemplates.{EmailJobFailedTemplate, EmailJobSuccessTemplate}
import org.specs2.mutable.Specification
import org.joda.time.{DateTime => JodaDateTime}

class MailSpec extends Specification{

  val jobId = 1234
  val jobUrl = new URL(s"http://my-domain:8454/#/analysis/jobs/$jobId")
  val createdAt = JodaDateTime.now()
  val smrtLinkVersion = Some("9.9.9")
  val emailInput = SmrtLinkEmailInput(new InternetAddress("user@mail.com"), jobId, "Job-Name", AnalysisJobStates.SUCCESSFUL, createdAt, createdAt, jobUrl, smrtLinkVersion)

  "Mail Template Rendering Smoke test" should {
    "render successful template" in {
      val templateResult = EmailJobSuccessTemplate(emailInput)
      //println(templateResult.html)
      true must beTrue
    }
    "render failed template" in {
      val templateResult = EmailJobFailedTemplate(emailInput.copy(jobState = AnalysisJobStates.FAILED))
      //println(templateResult.html)
      true must beTrue
    }
  }
}
