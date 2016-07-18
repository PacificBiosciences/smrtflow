package com.pacbio.secondary.smrtlink.app

import java.nio.file.{Files, Paths}

import com.pacbio.common.app.{AuthenticatedCoreProviders, BaseApi, BaseServer}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.configloaders.PbsmrtpipeConfigLoader
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.auth.SmrtLinkRolesInit
import com.pacbio.secondary.smrtlink.database.{DatabaseRunDaoProvider, DatabaseSampleDaoProvider}
import com.pacbio.secondary.smrtlink.models.DataModelParserImplProvider
import com.pacbio.secondary.smrtlink.services.jobtypes.{ImportDataSetServiceTypeProvider, MergeDataSetServiceJobTypeProvider, MockPbsmrtpipeJobTypeProvider}
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.logging.LoggerOptions
import com.typesafe.scalalogging.LazyLogging
import spray.servlet.WebBoot

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object SmrtLinkApp

trait SmrtLinkProviders extends
  AuthenticatedCoreProviders with
  JobManagerServiceProvider with
  SmrtLinkConfigProvider with
  PbsmrtpipeConfigLoader with
  JobsDaoActorProvider with
  JobsDaoProvider with
  SmrtLinkDalProvider with
  ProjectServiceProvider with
  DataSetServiceProvider with
  RunServiceProvider with
  SampleServiceProvider with
  RegistryServiceProvider with
  RunServiceActorRefProvider with
  SampleServiceActorRefProvider with
  RegistryServiceActorRefProvider with
  DatabaseRunDaoProvider with
  DatabaseSampleDaoProvider with
  ImportDataSetServiceTypeProvider with
  MergeDataSetServiceJobTypeProvider with
  MockPbsmrtpipeJobTypeProvider with
  InMemoryRegistryDaoProvider with
  DataModelParserImplProvider {
  override val actorSystemName = Some("smrtlink-smrt-server")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
}

trait SmrtLinkApi extends BaseApi with SmrtLinkRolesInit with LazyLogging {

  override val providers = new SmrtLinkProviders {}

  override def startup(): Unit = {
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
object SmrtLinkSmrtServer extends App with BaseServer with SmrtLinkApi {
  override val host = providers.serverHost()
  override val port = providers.serverPort()

  LoggerOptions.parseAddDebug(args)

  start
}

/**
 * Build our servlet app using tomcat
 *
 * <p> Note that the port used here is set in build.sbt when running locally
 * Used for running within tomcat via 'container:start'
 */
class SmrtLinkSmrtServerServlet extends WebBoot with SmrtLinkApi {
  override val serviceActor = rootService
}
