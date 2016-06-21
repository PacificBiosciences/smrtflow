package com.pacbio.secondary.smrtlink.actors

import akka.actor.{Props, ActorRef}
import com.pacbio.common.actors.ActorRefFactoryProvider
import com.pacbio.common.dependency.{Singleton, SetBindings, SetBinding}
import com.pacbio.secondary.analysis.engine.actors.EngineDaoActor


/**
 * Actors bound to this set will be notified when jobs complete, or state changes.
 * @see EngineDaoActor
 */
object JobListeners extends SetBinding[ActorRef]

trait EngineDaoActorProvider {
  this: ActorRefFactoryProvider
    with JobsDaoProvider
    with SetBindings =>

  // TODO(smcclellan): EngineDaoActor constructor 'listeners' argument should take a Set
  val engineDaoActor: Singleton[ActorRef] =
    Singleton(() => actorRefFactory().actorOf(
      Props(classOf[EngineDaoActor], jobsDao(), set(JobListeners).toSeq), "EngineDaoActor"))
}
