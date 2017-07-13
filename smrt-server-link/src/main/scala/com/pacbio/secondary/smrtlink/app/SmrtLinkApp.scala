package com.pacbio.secondary.smrtlink.app

import java.nio.file.{Files, Path}

import com.pacbio.common.app.{AuthenticatedCoreProviders, BaseApi, BaseServer}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.configloaders.PbsmrtpipeConfigLoader
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.database.{DatabaseRunDaoProvider, DatabaseSampleDaoProvider, DatabaseUtils}
import com.pacbio.secondary.smrtlink.models.DataModelParserImplProvider
import com.pacbio.secondary.smrtlink.services.jobtypes._
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.smrtlink.actors.AlarmManagerRunnerActor.RunAlarms
import com.pacbio.secondary.smrtlink.actors.DataIntegrityManagerActor.RunIntegrityChecks
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor.SubmitDbBackUpJob
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

object SmrtLinkApp

trait SmrtLinkProviders extends
  AuthenticatedCoreProviders with
  JobManagerServiceProvider with
  SmrtLinkConfigProvider with
  AlarmDaoActorProvider with
  AlarmRunnerLoaderProvider with
  AlarmManagerRunnerProvider with
  AlarmServiceProvider with
  SwaggerFileServiceProvider with
  PbsmrtpipeConfigLoader with
  PacBioBundleDaoActorProvider with
  PacBioDataBundlePollExternalActorProvider with
  PacBioBundleServiceProvider with
  EventManagerActorProvider with
  SmrtLinkEventServiceProvider with
  JobsDaoActorProvider with
  JobsDaoProvider with
  SmrtLinkDalProvider with
  DataIntegrityManagerActorProvider with
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
  DbBackUpServiceJobTypeProvider with
  InMemoryRegistryDaoProvider with
  DataModelParserImplProvider with
  SimpleLogServiceProvider {
  override val actorSystemName = Some("smrtlink-smrt-server")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
}

trait SmrtLinkApi extends BaseApi with LazyLogging with DatabaseUtils{

  override val providers = new SmrtLinkProviders {}

  override def startup(): Unit = {
    super.startup()

    val dataSource = providers.dbConfig.toDataSource

    // Is this necessary because there's not an explicit dep on this?
    val dataIntegrityManagerActor = providers.dataIntegrityManagerActor()
    val alarmManagerRunnerActor = providers.alarmManagerRunnerActor()

    // Setup Quartz Schedule
    // ALl the cron-esque tasks in SL should be folded back here
    // for consistency. For example, checking for chemistry bundle,
    // triggering alarm checks, checking status of event server,
    // data integrity checks.
    val scheduler = QuartzSchedulerExtension(system)

    providers.rootDataBaseBackUpDir() match {
      case Some(rootBackUpDir) =>

        val dbBackupKey = providers.conf.getString("pacBioSystem.dbBackUpSchedule")
        val m = SubmitDbBackUpJob(System.getProperty("user.name"), providers.dbConfig, rootBackUpDir)
        logger.info(s"Scheduling '$dbBackupKey' db backup $m")
        scheduler.schedule(dbBackupKey, providers.jobsDaoActor(), m)

        val alarmSchedule = providers.conf.getString("pacBioSystem.alarmSchedule")
        logger.info(s"Scheduling $alarmSchedule Alarm Manager Runner(s)")
        scheduler.schedule(alarmSchedule, alarmManagerRunnerActor, RunAlarms)

        val dataIntegritySchedule = providers.conf.getString("pacBioSystem.dataIntegritySchedule")
        logger.info(s"Scheduling $dataIntegritySchedule DataIntegrity Manager Runner")
        scheduler.schedule(dataIntegritySchedule, dataIntegrityManagerActor, RunIntegrityChecks)
      case _ =>
        logger.warn("System is not configured with a root database directory. Skipping scheduling Automated backups.")
    }

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