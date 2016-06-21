package com.pacbio.secondary.analysis.engine.app

import java.nio.file.{Paths, Files}
import java.util.UUID

import akka.actor.{ActorRef, Props, ActorRefFactory, ActorSystem}
import akka.util.Timeout
import com.pacbio.secondary.analysis.configloaders.EngineCoreConfigLoader
import com.pacbio.secondary.analysis.engine.EngineDao.{JobEngineDao, JobEngineDataStore}
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.engine.actors.{PipelineTemplateDaoActor, EngineManagerActor, EngineDaoActor}
import com.pacbio.secondary.analysis.jobs.{JobResourceResolver, PacBioIntJobResolver, SimpleJobRunner, SimpleUUIDJobResolver}
import com.pacbio.secondary.analysis.jobs.JobModels.{PipelineTemplate, JobEvent, EngineJob}
import com.pacbio.secondary.analysis.pipelines.PipelineTemplateDao

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Components that can be used via the Cake Pattern
 */
object AppComponents {

  trait EngineBase {
    implicit def system: ActorSystem

    def actorRefFactory: ActorRefFactory = system
  }

  /**
   * General config mixed-in values
   */
  trait EngineSystemConfig {
    val dao: JobEngineDataStore
    val resolver: JobResourceResolver
    val engineConfig: EngineConfig

  }

  trait PipelineTemplateConfig {
    val pipelineTemplates: Seq[PipelineTemplate]
  }

  trait EngineActorsBase {
    this: EngineBase with EngineSystemConfig with PipelineTemplateConfig =>

    val jobRunner = new SimpleJobRunner
    val listeners = Seq[ActorRef]()
    val engineDaoActor = system.actorOf(Props(new EngineDaoActor(dao, listeners)), "AppComponents$EngineDaoActor")
    val engineManagerActor = system.actorOf(Props(new EngineManagerActor(engineDaoActor, engineConfig, resolver, jobRunner)), "AppComponents$EngineManagerActor")

    val pipelineTemplateDao = new PipelineTemplateDao(pipelineTemplates)
    val pipelineTemplateActor = system.actorOf(Props(new PipelineTemplateDaoActor(pipelineTemplateDao)), "AppComponents$PipelineTemplateDaoActor")

  }

  object SimpleEngine extends EngineBase with EngineActorsBase with EngineSystemConfig with EngineCoreConfigLoader with PipelineTemplateConfig {

    //val rootJobDir = Paths.get(System.getProperty("user.dir")).resolve("job-root").toAbsolutePath.toString
    //val engineConfig = EngineConfig(5, "", rootJobDir)

    println(s"Running with job runner $jobRunner")
    //val pipelineTemplates = PbsmrtPipeTemplates.pbsmrtpipePipelineTemplates
    val pipelineTemplates = Seq[PipelineTemplate]()
    //val resolver = new SimpleUUIDJobResolver(Paths.get(engineConfig.pbRootJobDir))
    val resolver = new PacBioIntJobResolver(Paths.get(engineConfig.pbRootJobDir))
    val dao = new JobEngineDao(resolver)

    implicit val timeout = Timeout(10.seconds)
    implicit val system = ActorSystem("engine-actor-system")
  }

}
