package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Props, ActorRef}
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.engine.actors.EngineManagerActor
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.services.JobRunnerProvider


trait EngineManagerActorProvider {
  this: ActorRefFactoryProvider
    with SmrtLinkConfigProvider
    with EngineDaoActorProvider
    with JobRunnerProvider =>

  val engineManagerActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(
      Props(
        classOf[EngineManagerActor],
        engineDaoActor(),
        jobEngineConfig(),
        jobResolver(),
        jobRunner()
      ), "engine-manager"))
}