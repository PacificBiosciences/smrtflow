package com.pacbio.secondaryinternal

import java.nio.file.{Files, Path, Paths}

import scala.collection.mutable
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http
import spray.httpx.SprayJsonSupport
import com.pacbio.common.app.{BaseApi, BaseServer}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioService
import com.pacbio.logging.LoggerOptions

import com.pacbio.secondary.smrtserver.appcomponents.SecondaryAnalysisProviders
import com.pacbio.secondaryinternal.services.jobtypes.ConditionJobTypeServiceProvider


trait InternalServiceName {
  // This should be the new form to namespace
  // {system-id}/
  val baseServiceName = "smrt-analysis-internal"
}

trait BaseInternalMicroService extends PacBioService with InternalServiceName {
  override def prefixedRoutes =
    pathPrefix(separateOnSlashes(baseServiceName)) { super.prefixedRoutes }
}


trait SecondaryInternalAnalysisProviders extends SecondaryAnalysisProviders
    with SmrtLinkAnalysisInternalConfigProvider
    with ConditionJobTypeServiceProvider{

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_analysis_internal")
  override val actorSystemName = Some("slia")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  override val serverPort: Singleton[Int] = pbServices.getInt("port").orElse(8081)

}

trait SecondaryInternalAnalysisApi extends BaseApi
    with LazyLogging
    with SmrtLinkAnalysisInternalConfigProvider {

  override val providers = new SecondaryInternalAnalysisProviders {}

  def createIfNotExists(p: Path, message: String): Path = {
    if (!Files.exists(p)) {
      logger.info(s"Creating $message $p")
      Files.createDirectories(p)
    } else {
      logger.info(s"Using $message $p")
    }
    p
  }

  override def startup(): Unit = {
    createIfNotExists(Paths.get(providers.engineConfig.pbRootJobDir), "Engine Job Dir")
    createIfNotExists(providers.reseqConditions(), "Resequencing Condition Dir")

  }

  sys.addShutdownHook(system.shutdown())

}


/**
 * This is used for spray-can http server which can be started via 'sbt run'
 */
object SecondaryAnalysisInternalServer extends App
  with BaseServer
  with SecondaryInternalAnalysisApi {

  override val host = providers.serverHost()
  override val port = providers.serverPort()
  println(s"serverPort: ${providers.serverPort()}")

  LoggerOptions.parseAddDebug(args)

  start
}
