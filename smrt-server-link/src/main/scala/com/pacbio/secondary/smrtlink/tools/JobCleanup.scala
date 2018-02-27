package com.pacbio.secondary.smrtlink.tools

import java.nio.file.Paths

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.{Properties, Try}

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.tools._
import com.pacbio.secondary.smrtlink.client._

case class JobCleanupConfig(host: String,
                            port: Int,
                            minAge: Int = 6,
                            maxTime: FiniteDuration = 30.minutes,
                            dryRun: Boolean = false)
    extends LoggerConfig

object JobCleanup extends ClientAppUtils with LazyLogging {
  private def andLog(sx: String): Future[String] = Future {
    logger.info(sx)
    sx
  }
  protected def cleanupJob(sal: SmrtLinkServiceClient,
                           job: EngineJob): Future[Int] =
    for {
      _ <- andLog(
        s"Job ${job.id} (${job.state.toString}) last updated ${job.updatedAt.toString}")
      _ <- sal.deleteJob(job.uuid)
    } yield 1

  def apply(sal: SmrtLinkServiceClient, c: JobCleanupConfig): Future[String] = {
    val cutoff = JodaDateTime.now().minusMonths(c.minAge)

    def runCleanUp(client: SmrtLinkServiceClient,
                   job: EngineJob): Future[Int] = {
      if (c.dryRun) Future.successful(0)
      else if (Paths.get(job.path).resolve("DO_NOT_DELETE").toFile.exists) {
        logger.info(s"Skipping protected job ${job.id}") // XXX EVIL
        Future.successful(0)
      } else cleanupJob(client, job)
    }

    def filterJob(job: EngineJob): Boolean = {
      (job.state.isCompleted &&
      job.updatedAt.isBefore(cutoff) &&
      !AnalysisJobStates.isSuccessful(job.state))
    }

    for {
      _ <- andLog(s"Will remove all failed jobs prior to ${cutoff.toString}")
      allJobs <- sal.getAnalysisJobs
      jobs <- Future.successful(allJobs.filter(filterJob))
      results <- Future.sequence(jobs.map(job => runCleanUp(sal, job)))
      total <- Future.successful(results.reduceLeftOption(_ + _).getOrElse(0))
      msg <- andLog(s"Deleted $total jobs")
    } yield msg
  }
}

object JobCleanupTool extends CommandLineToolRunner[JobCleanupConfig] {

  override val VERSION = "0.1.0"
  override val toolId = "job_cleanup"
  override val DESCRIPTION =
    """
      |Tool to bulk-delete failed jobs on a SMRT Link server.
    """.stripMargin

  lazy val defaultHost: String =
    Properties.envOrElse("PB_SERVICE_HOST", "localhost")
  lazy val defaultPort: Int =
    Properties.envOrElse("PB_SERVICE_PORT", "8070").toInt
  lazy val defaults = JobCleanupConfig(defaultHost, defaultPort)

  lazy val parser =
    new OptionParser[JobCleanupConfig]("smrt-link-job-cleanup") {
      head(DESCRIPTION, VERSION)
      opt[String]("host")
        .action((x, c) => c.copy(host = x))
        .text(
          s"Hostname of smrtlink server (default: $defaultHost).  Override the default with env PB_SERVICE_HOST.")

      opt[Int]("port")
        .action((x, c) => c.copy(port = x))
        .text(
          s"Services port on smrtlink server (default: $defaultPort).  Override default with env PB_SERVICE_PORT.")

      opt[Int]("age")
        .action((x, c) => c.copy(minAge = x))
        .text("Minimum age of jobs to delete (default = 6 months)")

      opt[Int]("timeout")
        .action((t, c) => c.copy(maxTime = t.seconds))
        .text(
          s"Maximum time to poll for running job status in seconds (Default ${defaults.maxTime})")

      opt[Unit]("dry-run")
        .action((x, c) => c.copy(dryRun = true))
        .text("Find jobs to delete without actually removing anything")

      LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

      opt[Unit]('h', "help")
        .action((x, c) => { showUsage; sys.exit(0) })
        .text("Show options and exit")

      override def showUsageOnError = false
    }

  override def runTool(c: JobCleanupConfig): Try[String] = {
    implicit val actorSystem = ActorSystem("pbservice")
    try {
      val sal = new SmrtLinkServiceClient(c.host, c.port)(actorSystem)
      runAndBlock(JobCleanup(sal, c), c.maxTime)
    } finally {
      actorSystem.terminate()
    }
  }

  def run(c: JobCleanupConfig): Either[ToolFailure, ToolSuccess] =
    Left(ToolFailure(toolId, 0, "NOT Supported"))
}

object JobCleanupApp extends App {
  import JobCleanupTool._
  runnerWithArgsAndExit(args)
}
