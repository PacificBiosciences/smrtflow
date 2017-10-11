import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.{
  CommandTemplate,
  CommandTemplateJob
}
import org.specs2.mutable._

/**
  * Tests for rendering "cluster" template commands
  * Created by mkocher on 10/13/15.
  */
class PbsmrtpipeCommandTemplateSpec extends Specification {

  sequential

  "Simple template " should {

    "Render simple template" in {

      val jobId = "job-01"
      val stderr = "/tmp/stderr.txt"
      val stdout = "/tmp/stdout.txt"
      val nproc = 12
      val cmd = "ls -ls /tmp"
      val sx =
        """my-command-runner ${JOB_ID} ${NPROC} ${STDOUT_FILE} ${STDERR_FILE} "${CMD}" """

      val renderCorrect = s"my-command-runner $jobId $nproc $stdout $stderr " + '"' + cmd + "\" "
      val st = new CommandTemplate(sx)
      val commandTemplateJob = CommandTemplateJob(jobId,
                                                  nproc,
                                                  Paths.get(stdout),
                                                  Paths.get(stderr),
                                                  cmd)

      val rendered = st.render(commandTemplateJob)
      rendered must beEqualTo(renderCorrect)
    }

  }

}
