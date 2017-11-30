package com.pacbio.secondary.smrtlink

import java.net.URL
import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
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
    lazy val swaggerJson = "eventserver_swagger.json"
    // Note, this must be consistent with the how the server is launched.
    lazy val eveUrl = new URL(s"https:$systemHost:$systemPort")
  }

  trait EventServiceConfigCakeProvider extends BaseServiceConfigCakeProvider {

    override lazy val systemName = "smrt-eve"
    // This should be loaded from the application.conf with an ENV var mapping
    lazy val eventMessageDir: Path =
      Paths.get(conf.getString("smrtflow.event.eventRootDir")).toAbsolutePath
    // Make this independently configurable
    lazy val eventUploadFilesDir: Path = eventMessageDir.resolve("files")
  }

  trait ActorSystemCakeProvider { this: BaseServiceConfigCakeProvider =>
    implicit lazy val actorSystem = ActorSystem(systemName)
  }

}
