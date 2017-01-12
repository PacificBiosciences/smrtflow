package com.pacbio.secondary.analysis.tools

import java.nio.file.{Paths, Files}
import java.util.UUID

import com.pacbio.secondary.analysis.configloaders.PbsmrtpipeConfigLoader
import com.pacbio.secondary.analysis.jobs.{PrinterJobResultsWriter, AnalysisJobStates}
import com.pacbio.secondary.analysis.jobs.JobModels.{JobResource, BoundEntryPoint}
import com.pacbio.secondary.analysis.jobtypes.PbSmrtPipeJobOptions
import com.pacbio.secondary.analysis.pbsmrtpipe.IOUtils
import com.typesafe.scalalogging.LazyLogging

/**
 *
 * Hacky way for testing calling pbsmrtpipe pipelines and running
 * a basic sanity test
 */
object PbSmrtpipeHackyTest extends App with LazyLogging {

  val pipelineId = "pbsmrtpipe.pipelines.dev_01"
  val outputDir = Files.createTempDirectory("pbsmrtpipe-jobOptions")
  val envShellWrapper = Some(Paths.get("/Users/mkocher/.virtualenvs/p4_pbsmrtpipe_test/bin/activate"))

  val f = "/Users/mkocher/gh_projects/pbsmrtpipe_dev/pbsmrtpipe/testkit-data/dev_01/preset.xml"
  val cmdTemplate = PbsmrtpipeConfigLoader.loadCmdTemplate

  println(s"Command template $cmdTemplate")
  val x = IOUtils.parsePresetXml(Paths.get(f))
  val taskOpts = x.taskOptions
  val workflowOpts = x.options

  val epath = outputDir.resolve("my-file.txt")
  val ePoints = Seq(BoundEntryPoint("e_01", epath.toString))
  IOUtils.writeMockBoundEntryPoints(epath)

  val serviceUri = None
  val cmd = IOUtils.toCmd(ePoints, pipelineId, outputDir, taskOpts, workflowOpts, serviceUri)
  println(s"Command '$cmd'")

  val jobResource = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
  val opts = PbSmrtPipeJobOptions(pipelineId, ePoints, taskOpts, workflowOpts, envShellWrapper, serviceUri, cmdTemplate)
  val job = opts.toJob

  val writer = new PrinterJobResultsWriter
  logger.info(s"Job $job")
  logger.debug(s"Running jobOptions in ${outputDir.toAbsolutePath.toString}")
  val jobResults = job.run(jobResource, writer)

  val exitCode = jobResults match {
    case Right(result) => logger.info("Job was successful"); 0
    case Left(result) => logger.error(s"Job failed $result"); 1
  }
  println(s"exiting pbsmrtpipe hacky main with exit code $exitCode")
  System.exit(exitCode)
}

