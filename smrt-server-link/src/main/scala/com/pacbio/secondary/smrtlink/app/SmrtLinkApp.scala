package com.pacbio.secondary.smrtlink.app

import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorRef
import com.pacbio.common.app.{AuthenticatedCoreProviders, BaseApi, BaseServer}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.configloaders.PbsmrtpipeConfigLoader
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.database.{DatabaseRunDaoProvider, DatabaseSampleDaoProvider, DatabaseUtils}
import com.pacbio.secondary.smrtlink.models.DataModelParserImplProvider
import com.pacbio.secondary.smrtlink.services.jobtypes.{DeleteJobServiceTypeProvider, ImportDataSetServiceTypeProvider, MergeDataSetServiceJobTypeProvider, MockPbsmrtpipeJobTypeProvider}
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.logging.LoggerOptions
import com.typesafe.scalalogging.LazyLogging
import spray.servlet.WebBoot

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

object SmrtLinkApp

trait SmrtLinkProviders extends
  AuthenticatedCoreProviders with
  JobManagerServiceProvider with
  SmrtLinkConfigProvider with
  PbsmrtpipeConfigLoader with
  AutomationConstraintServiceProvider with
  PacBioBundleServiceProvider with
  EventManagerActorProvider with
  JobsDaoActorProvider with
  JobsDaoProvider with
  SmrtLinkDalProvider with
  EulaServiceProvider with
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
  DeleteJobServiceTypeProvider with
  InMemoryRegistryDaoProvider with
  DataModelParserImplProvider {
  override val actorSystemName = Some("smrtlink-smrt-server")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
}

trait SmrtLinkApi extends BaseApi with LazyLogging with DatabaseUtils{

  override val providers = new SmrtLinkProviders {}

  override def startup(): Unit = {
    super.startup()

    // This is necessary for the Actor to get created from the Singleton???
    val eventManagerActorX = providers.eventManagerActor()
    val dataSource = providers.dbConfig.toDataSource

    def createJobDir(path: Path): Path = {
      if (!Files.exists(path)) {
        logger.info(s"Creating root job dir $path")
        Files.createDirectories(path)
      }
      path.toAbsolutePath
    }

    // Start Up validation
    val startUpValidation = for {
      connMessage <- Future { TestConnection(dataSource)}
      migrationMessage <- Future { Migrator(dataSource)}
      summary <- providers.jobsDao().getSystemSummary("Database Startup Test")
      jobDir <- Future { createJobDir(providers.engineConfig.pbRootJobDir)}
    } yield s"$connMessage\n$migrationMessage\n$summary\nCreated Job Root $jobDir"

    startUpValidation.andThen { case _ => dataSource.close()}

    startUpValidation.onFailure {
      case NonFatal(e) =>
        val emsg = s"Failed to connect or perform migration on database"
        logger.error(emsg, e)
        System.err.println(emsg + e.getMessage)
        System.exit(1)
    }

    startUpValidation.onSuccess {
      case summary:String =>
        logger.info(summary)
        println(summary)
    }

    logger.info(Await.result(startUpValidation, Duration.Inf))

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
