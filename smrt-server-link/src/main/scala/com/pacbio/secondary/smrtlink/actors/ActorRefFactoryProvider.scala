package com.pacbio.secondary.smrtlink.actors

import akka.actor.{ActorRefFactory, ActorSystem}
import com.pacbio.secondary.smrtlink.dependency.{ConfigProvider, Singleton}

/**
 * Abstract provider for a singleton ActorRefFactory. Concrete providers must define the actorRefFactory val.
 */
trait ActorRefFactoryProvider {
  val actorRefFactory: Singleton[ActorRefFactory]
}

/**
 * Provider for a singleton ActorSystem. Also implements ActorRefFactoryProvider. Concrete providers may optionally
 * override the actorSystemName val.
 */
trait ActorSystemProvider extends ActorRefFactoryProvider {
  this: ConfigProvider =>

  val actorSystemName: Option[String] = None

  val actorSystem: Singleton[ActorSystem] =
    Singleton(() => ActorSystem(actorSystemName.getOrElse("default"), config()))

  override val actorRefFactory: Singleton[ActorRefFactory] = actorSystem
}
