package com.pacbio.secondary.smrtlink

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.event.Logging.LogLevel
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.{
  DebuggingDirectives,
  LogEntry,
  LoggingMagnet
}
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

  trait ServiceLoggingUtils {
    private def akkaResponseTimeLoggingFunction(
        loggingAdapter: LoggingAdapter,
        requestTimestamp: Long,
        level: LogLevel = Logging.InfoLevel)(req: HttpRequest)(
        res: RouteResult): Unit = {
      val entry = res match {
        case Complete(resp) =>
          val responseTimestamp: Long = System.nanoTime
          val elapsedTime
            : Long = (responseTimestamp - requestTimestamp) / 1000000
          val loggingString =
            s"""Logged Request:${req.method.value}:${req.uri}:${resp.status}:$elapsedTime ms"""
          LogEntry(loggingString, level)
        case Rejected(reason) =>
          LogEntry(s"Rejected Reason: ${reason.mkString(",")}", level)
      }
      entry.logTo(loggingAdapter)
    }

    private def logResponseTime(log: LoggingAdapter) = {
      val requestTimestamp = System.nanoTime
      akkaResponseTimeLoggingFunction(log, requestTimestamp)(_)
    }

    /**
      * Wrap the routes with the logging of the Response Time
      *
      * @param routes original routes
      * @return
      */
    def logResponseTimeRoutes(routes: Route) =
      DebuggingDirectives.logRequestResult(LoggingMagnet(logResponseTime))(
        routes)
  }

}
