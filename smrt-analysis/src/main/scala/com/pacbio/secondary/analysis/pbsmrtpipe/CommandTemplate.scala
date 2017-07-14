package com.pacbio.secondary.analysis.pbsmrtpipe

import java.nio.file.Path


trait RenderCommandTemplate {

  // Allowed Values in the cluster template
  val JOB_ID = "JOB_ID"
  val NPROC = "NPROC"
  val STDERR = "STDERR_FILE"
  val STDOUT = "STDOUT_FILE"
  val CMD = "CMD"

  def renderTemplate(template: String, datum: Map[String, String]): String = {
    datum.foldLeft(template)((s:String, x:(String, String)) => ( """\$\{""" + x._1 + "\\}" ).r.replaceAllIn( s, x._2 ))
  }
  def renderCommandTemplate(template: String, job: CommandTemplateJob) = {
    val datum = Map(
      JOB_ID -> job.jobId,
      NPROC -> job.nproc.toString,
      STDOUT -> job.stdout.toAbsolutePath.toString,
      STDERR -> job.stderr.toAbsolutePath.toString,
      CMD -> job.cmd
    )
    renderTemplate(template, datum)
  }
}

object RenderCommandTemplate extends RenderCommandTemplate

/**
 * Simple Template mechanism to Generate a job.sh "command"
 *
 * The template has 5 supported values.
 *
 *
 * @param template
 */
case class CommandTemplate(template: String) {

  def render(job: CommandTemplateJob): String = {
    //  my-command-runner ${JOB_ID} ${NPROC} ${STDERR} ${STDOUT} ${CMD}"
    RenderCommandTemplate.renderCommandTemplate(template, job)
  }
}

case class CommandTemplateJob(jobId: String, nproc: Int, stdout: Path, stderr: Path, cmd: String)

