package com.pacbio.secondary.analysis.engine.app

import java.nio.file.{Paths, Files}
import java.util.UUID

import akka.actor._
import akka.util.Timeout
import com.pacbio.secondary.analysis.bio.FastaMockUtils
import com.pacbio.secondary.analysis.configloaders.PbsmrtpipeConfigLoader
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.engine.EngineDao.JobEngineDao
import com.pacbio.secondary.analysis.engine.actors.PipelineTemplateDaoActor.GetAllPipelineTemplates
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.engine.actors.{PipelineTemplateDaoActor, EngineManagerActor, EngineDaoActor}
import com.pacbio.secondary.analysis.engine.CommonMessages.{GetSystemJobSummary, AddNewJob, CheckForRunnableJob, GetAllJobs}
import com.pacbio.secondary.analysis.jobs._
import JobModels._
import com.pacbio.secondary.analysis.jobtypes._
import com.pacbio.secondary.analysis.pbsmrtpipe.{CommandTemplate, PbsmrtpipeEngineOptions, IOUtils}
import com.pacbio.secondary.analysis.pipelines.PipelineTemplateDao
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait DemoJobs {
  def toJob(engineConfig: EngineConfig): CoreJob

  def toJobs(n: Int, engineConfig: EngineConfig): Seq[CoreJob] = (0 until n).map(x => toJob(engineConfig))
}

object ImportDataSetDemoJobs extends DemoJobs {

  def toJob(engineConfig: EngineConfig) = {
    val resource = getClass.getResource("/example_subreads.xml")
    println(s"Getting resource $resource")
    val path = Paths.get(resource.toURI)
    val jobConfig = ImportDataSetOptions(path.toAbsolutePath.toString, DataSetMetaTypes.Subread)
    CoreJob(UUID.randomUUID(), jobConfig)
  }
}

object SimpleDemoJobs extends DemoJobs {
  def toJob(engineConfig: EngineConfig) = {
    val jobConfig = SimpleDevJobOptions(1, 7)
    CoreJob(UUID.randomUUID(), jobConfig)
  }
}

object SimpleDemoDevDataStoreJobs extends DemoJobs {
  def toJob(engineConfig: EngineConfig) = {
    val maxMockFiles = 10
    val jobConfig = SimpleDevDataStoreJobOptions(maxMockFiles)
    CoreJob(UUID.randomUUID(), jobConfig)
  }
}

object PbSmrtPipeMockDemoJobs extends DemoJobs {

  def toJob(engineConfig: EngineConfig) = {
    val pipelineId = "pbscala.pipelines.mock_pipeline"
    val boundEntryPoints = Seq(
      BoundEntryPoint("e_01", "/path/to/file.txt"),
      BoundEntryPoint("e_02", "/path/to/file2.txt"))

    val taskOptions = Seq[PipelineIntOptionBase]()
    val workflowOptions = Seq[PipelineIntOptionBase]()
    val jobConfig = MockPbSmrtPipeJobOptions(pipelineId, boundEntryPoints, taskOptions, workflowOptions, "")

    CoreJob(UUID.randomUUID(), jobConfig)
  }
}

object PbSmrtPipeDemoJobs extends DemoJobs with LazyLogging {

  def toOpts(engineConfig: EngineConfig) = {
    val pipelineId = "pbsmrtpipe.pipelines.dev_01"
    // Write mock input files here. Output files will be written via the Resolver
    val outputDir = Files.createTempDirectory("pbsmrtpipe-demo-inputs")

    val taskOptions = Seq[PipelineIntOptionBase]()
    val workflowOptions = Seq[PipelineIntOptionBase]()
    val epath = outputDir.resolve("my-file.txt")
    val entryPoints = Seq(BoundEntryPoint("e_01", epath.toString))
    IOUtils.writeMockBoundEntryPoints(epath)

    val serviceUri = None
    PbSmrtPipeJobOptions(pipelineId, entryPoints, taskOptions, workflowOptions, engineConfig.pbToolsEnv, serviceUri)
  }

  def toJob(engineConfig: EngineConfig) = {
    val jobConfig = toOpts(engineConfig)
    CoreJob(UUID.randomUUID(), jobConfig)
  }

  def toCustomJob(engineConfig: EngineConfig, pbsmrtpipeEngineConfig: PbsmrtpipeEngineOptions, cmdTemplate: Option[CommandTemplate]) = {
    val opts = toOpts(engineConfig)
    val customOpts = opts.copy(workflowOptions = pbsmrtpipeEngineConfig.toPipelineOptions, commandTemplate = cmdTemplate)
    CoreJob(UUID.randomUUID(), customOpts)
  }
}

object DemoConvertImportFastaJob extends DemoJobs {
  def toJob(engineConfig: EngineConfig) = {
    val fastaPath = "/tmp/file.fasta"
    val opts = ConvertImportFastaOptions(fastaPath, "ref-name", "diploid", "example-organism")
    CoreJob(UUID.randomUUID(), opts)
  }
}

/**
 * Demo Example of using the JobExecution layer
 */
object SimpleEngineApp extends App with LazyLogging {

  val outputDir = Paths.get(System.getProperty("user.dir")).resolve("jobs-root")
  if (!Files.exists(outputDir)) {
    println("Creating root directory for job output.")
    Files.createDirectory(outputDir)
  }

  val engineConfig = PbsmrtpipeConfigLoader.loadFromAppConf
  val pbsmrtpipeEngineConfig = PbsmrtpipeConfigLoader.loadPbsmrtpipeEngineConfigOrDefaults
  val cmdTemplate = PbsmrtpipeConfigLoader.loadCmdTemplate
  println(s"Custom cmd template $cmdTemplate")

  val njobs = 4

  val xjobs = Seq(
    SimpleDemoJobs,
    SimpleDemoDevDataStoreJobs,
    PbSmrtPipeMockDemoJobs,
    PbSmrtPipeDemoJobs,
    DemoConvertImportFastaJob,
    ImportDataSetDemoJobs).flatMap(x => x.toJobs(njobs, engineConfig))


  //Pbsmrtpie jobs with custom engine options
  val jobs = xjobs ++ (0 until 10).map(x => PbSmrtPipeDemoJobs.toCustomJob(engineConfig, pbsmrtpipeEngineConfig, cmdTemplate))

  logger.info(s"Starting simple-engine-demo-app with ${jobs.length}")
  logger.info(s"Engine config $engineConfig")
  logger.info(s"pbsmrtpipe engine config $pbsmrtpipeEngineConfig")

  // This is a necessary test file
  val fastaPath = "/tmp/file.fasta"
  val p = Paths.get(fastaPath)
  FastaMockUtils.writeMockFastaFile(100, p)
  logger.info(s"Wrote mock fasta to $p")

  //val resolver = new SimpleUUIDJobResolver(Paths.get(engineConfig.pbRootJobDir))
  val resolver = new PacBioIntJobResolver(engineConfig.pbRootJobDir)

  val dao = new JobEngineDao(resolver)

  implicit val timeout = Timeout(10.seconds)
  implicit val system = ActorSystem("engine-actor-system")

  val listeners = Seq[ActorRef]()

  val engineDaoActor = system.actorOf(Props(new EngineDaoActor(dao, listeners)), "SimpleEngineApp$EngineDaoActor")
  //val jobRunner = new SimpleJobRunner
  val jobRunner = new SimpleAndImportJobRunner(engineDaoActor)
  val engineManagerActor = system.actorOf(Props(new EngineManagerActor(engineDaoActor, engineConfig, resolver, jobRunner)), "SimpleEngineApp$EngineManagerActor")

  val pipelineTemplates = Seq[PipelineTemplate]()
  val pipelineTemplateDao = new PipelineTemplateDao(pipelineTemplates)
  val pipelineTemplateActor = system.actorOf(Props(new PipelineTemplateDaoActor(pipelineTemplateDao)), "SimpleEngineActor$PipelineTemplateDaoActor")

  val debug = true
  val c0 = system.scheduler.scheduleOnce(5.second, pipelineTemplateActor, GetAllPipelineTemplates)
  //val c1 = system.scheduler.schedule(1.second, 5.second, engineManagerActor, CheckForRunnableJob)
  val c4 = system.scheduler.schedule(1.second, 10.second, engineDaoActor, GetSystemJobSummary)

  val cs = Seq(c0, c4)

  // Add Tasks to be run
  jobs.foreach { job => engineManagerActor ! AddNewJob(job) }

  scala.io.StdIn.readLine("Press any key to exit ...\n")

  // Cancel All scheduled messages
  cs.foreach { c =>
    c.cancel()
  }

  println("Sending shutdown message.")

  println("Printing final results and sending shutdown messages")
  system.scheduler.scheduleOnce(0.5.seconds, engineDaoActor, GetSystemJobSummary)

  // The order these are shutdown is important
  system.scheduler.scheduleOnce(2.second, engineManagerActor, PoisonPill)
  system.scheduler.scheduleOnce(2.second, engineDaoActor, PoisonPill)

  scala.io.StdIn.readLine("Press any other key to exit (for real this time) ...\n")

  system.shutdown()
  println("Exiting main.")
}
