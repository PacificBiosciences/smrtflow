package com.pacbio.secondaryinternal

import com.pacbio.common.app.{BaseApi, BaseServer}
import com.pacbio.common.dependency.{Singleton, TypesafeSingletonReader}
import com.pacbio.common.services.PacBioService
import com.pacbio.secondaryinternal.daos._
import com.pacbio.secondaryinternal.models.ReferenceSetResource
import com.pacbio.secondaryinternal.services.{LimsResolverServiceProvider, ReferenceSetResolverServiceProvider, SmrtLinkResourceServiceProvider}
import spray.can.Http
import spray.httpx.SprayJsonSupport
import com.pacbio.common.services._
import spray.servlet.WebBoot

import scala.collection.mutable
import scala.util.matching.Regex

trait InternalServiceName {
  val baseServiceName = "api/v1/smrt-ianalysis"
}

trait BaseInternalMicroService extends PacBioService with InternalServiceName {
  override def prefixedRoutes =
    pathPrefix(separateOnSlashes(baseServiceName)) { super.prefixedRoutes }
}

trait SecondaryInternalAnalysisProviders extends
    tempbase.CoreProviders with
    LimsDaoProvider with
    LimsResolverServiceProvider with
    SmrtLinkResourceServiceProvider with
    ConstantSmrtLinkResourceDaoProvider with
    ReferenceSetResolverServiceProvider with
    InitiallyEmptyReferenceResourceDaoProvider {

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_analysis_internal")
  override val actorSystemName = Some("smrtlink-analysis-internal-server")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  override val serverPort: Singleton[Int] =
    TypesafeSingletonReader.fromConfig().getInt("port").orElse(8090)
}

trait SecondaryInternalAnalysisApi extends BaseApi {
  override val providers = new SecondaryInternalAnalysisProviders {}
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

/**
 * This is used for spray-can http server which can be started via 'sbt run'
 */
object SecondaryAnalysisInternalServer extends App with BaseServer with BaseApi {
  override val providers = new SecondaryInternalAnalysisProviders {}
  override val host = providers.serverHost()
  override val port = providers.serverPort()
  println(s"serverPort: ${providers.serverPort()}")

  //override def startup(): Unit = providers.cleanupScheduler().scheduleAll()

  start
}

/**
 * Build our servlet app using tomcat
 *
 * <p> Note that the port used here is set in build.sbt when running locally
 * Used for running within tomcat via 'container:start'
 */
class SecondaryInternalAnalysisServlet extends WebBoot with BaseApi {
  override val providers = new SecondaryInternalAnalysisProviders {}
  override val serviceActor = rootService
}
