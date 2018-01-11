package com.pacbio.secondary.smrtlink

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.pacbio.secondary.smrtlink.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.utils.SmrtServerIdUtils

package object app {

  /**
    * Build the App using the "Cake" Patten leveraging "self" traits.
    * This method defined a model to build the app in type-safe way.
    *
    * Adding "Cake" to the naming to avoid name collisions with the
    * Singleton Providers approach.
    *
    * Note that every definition must use a lazy val or def to use the
    * cake pattern correctly.
    */
  trait BaseServiceConfigCakeProvider
      extends ConfigLoader
      with SmrtServerIdUtils {
    lazy val systemName = "smrt-server"
    lazy val systemPort = conf.getInt("smrtflow.server.port")
    lazy val systemHost = "0.0.0.0"
    lazy val systemUUID = getSystemUUID(conf)
    lazy val apiSecret = conf.getString("smrtflow.event.apiSecret")
  }

  trait ActorSystemCakeProvider { this: BaseServiceConfigCakeProvider =>
    implicit lazy val actorSystem = ActorSystem(systemName)
    implicit lazy val materializer = ActorMaterializer()
  }

}
