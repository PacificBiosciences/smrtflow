package com.pacbio.secondary.smrtlink.tools

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.Properties

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.tools._
import com.pacbio.secondary.smrtlink.client._

object JobCleanupParser extends CommandLineToolVersion {

  val VERSION = "0.1.0"
  val TOOL_ID = "pbscala.tools.job_cleanup"

  case class JobCleanupConfig(host: String,
                              port: Int,
                              minAge: Int = 6,
                              maxTime: FiniteDuration = 30.minutes,
                              dryRun: Boolean = false)
      extends LoggerConfig

  lazy val defaultHost: String =
    Properties.envOrElse("PB_SERVICE_HOST", "localhost")
  lazy val defaultPort: Int =
    Properties.envOrElse("PB_SERVICE_PORT", "8070").toInt
  lazy val defaults = JobCleanupConfig(defaultHost, defaultPort)

  lazy val parser =
    new OptionParser[JobCleanupConfig]("smrt-link-job-cleanup") {
      head("PacBio SMRT Link server failed job cleanup", VERSION)
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
}

object JobCleanup extends ClientAppUtils with LazyLogging {
  protected def cleanupJob(sal: SmrtLinkServiceClient,
                           job: EngineJob,
                           dryRun: Boolean = false): Future[Int] = {
    logger.info(
      s"Job ${job.id} (${job.state.toString}) last updated ${job.updatedAt.toString}")
    if (!dryRun) {
      sal
        .deleteJob(job.uuid)
        .map(_ => 1)
    } else {
      Future.successful(1)
    }
  }
  def apply(c: JobCleanupParser.JobCleanupConfig): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    try {
      val cutoff = JodaDateTime.now().minusMonths(c.minAge)
      logger.info(s"Will remove all failed jobs prior to ${cutoff.toString}")
      val sal = new SmrtLinkServiceClient(c.host, c.port)(actorSystem)
      val jobs = Await.result(sal.getAnalysisJobs, c.maxTime)
      val nJobs =
        jobs
          .filter(job => job.state.isCompleted)
          .filter(job => job.updatedAt.isBefore(cutoff))
          .filter(job => !AnalysisJobStates.isSuccessful(job.state))
          .map(job => Await.result(cleanupJob(sal, job, c.dryRun), c.maxTime))
          .sum
      logger.info(s"Deleted $nJobs jobs")
      0
    } finally {
      actorSystem.terminate()
    }
  }
}

object JobCleanupApp extends App with LazyLogging {
  def run(args: Seq[String]) = {
    val xc = JobCleanupParser.parser
      .parse(args.toSeq ++ Seq("--log2stdout"), JobCleanupParser.defaults) match {
      case Some(config) =>
        logger.debug(s"Args $config")
        JobCleanup(config)
      case _ => 1
    }
    sys.exit(xc)
  }
  run(args)
}
