package com.pacbio.secondaryinternal

import java.nio.file.{Files, Paths}

import scala.collection.mutable
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http
import spray.httpx.SprayJsonSupport
import com.pacbio.common.app.{BaseApi, BaseServer}
import com.pacbio.common.dependency.{Singleton, TypesafeSingletonReader}
import com.pacbio.common.services.PacBioService
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondaryinternal.daos._
import com.pacbio.secondaryinternal.models.ReferenceSetResource
import com.pacbio.secondary.smrtlink.auth.SmrtLinkRolesInit
import com.pacbio.secondaryinternal.services.jobtypes.ConditionJobTypeServiceProvider

//import com.pacbio.secondaryinternal.services.jobtypes.ConditionJobTypeServiceProvider
import com.pacbio.secondary.smrtserver.appcomponents.SecondaryAnalysisProviders
//import com.pacbio.secondaryinternal.services.{LimsResolverServiceProvider, ReferenceSetResolverServiceProvider, SmrtLinkResourceServiceProvider}




trait InternalServiceName {
  // This should be the new form to namespace
  // {system-id}/
  val baseServiceName = "smrt-analysis-internal"
}

trait BaseInternalMicroService extends PacBioService with InternalServiceName {
  override def prefixedRoutes =
    pathPrefix(separateOnSlashes(baseServiceName)) { super.prefixedRoutes }
}

trait ConstantSmrtLinkResourceDaoProvider extends
  SmrtLinkResourceDaoProvider {
  val smrtLinkResourceDao: Singleton[SmrtLinkResourceDao] =
    Singleton(() => new InMemorySmrtLinkResourceDao(Constants.SL_SYSTEMS))
}

trait InitiallyEmptyReferenceResourceDaoProvider extends
  ReferenceResourceDaoProvider {
  val referenceResourceDao =
    Singleton(() => new InMemoryReferenceResourceDao(mutable.Set.empty[ReferenceSetResource]))
}

trait SecondaryInternalAnalysisProviders extends
  SecondaryAnalysisProviders
//  with LimsDaoProvider
//  with LimsResolverServiceProvider
//  with SmrtLinkResourceServiceProvider
//  with ConstantSmrtLinkResourceDaoProvider
//  with ReferenceSetResolverServiceProvider
//  with InitiallyEmptyReferenceResourceDaoProvider
  with ConditionJobTypeServiceProvider {

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_analysis_internal")
  override val actorSystemName = Some("smrtlink-analysis-internal-server")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  override val serverPort: Singleton[Int] = pbServices.getInt("port").orElse(8081)

}

trait SecondaryInternalAnalysisApi extends BaseApi with SmrtLinkRolesInit with LazyLogging {
  override val providers = new SecondaryInternalAnalysisProviders {}

  override def startup(): Unit = {
    try {
      providers.jobsDao().initializeDb()
    } catch {
      case e: Exception => {
        e.printStackTrace()
        system.shutdown()
      }
    }

    val p = Paths.get(providers.engineConfig.pbRootJobDir)
    if (!Files.exists(p)) {
      logger.info(s"Creating root job dir $p")
      Files.createDirectories(p)
    }
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
