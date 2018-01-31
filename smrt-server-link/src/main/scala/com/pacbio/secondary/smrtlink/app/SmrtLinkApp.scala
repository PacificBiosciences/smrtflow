package com.pacbio.secondary.smrtlink.app

import scala.collection.JavaConverters._
import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path}

import akka.pattern._
import akka.util.Timeout
import akka.actor.ActorRefFactory
import akka.event.Logging.LogLevel
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.{
  DebuggingDirectives,
  LogEntry,
  LoggingMagnet
}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.pacbio.secondary.smrtlink.dependency.{
  DefaultConfigProvider,
  SetBindings,
  Singleton
}
import com.pacbio.secondary.smrtlink.analysis.configloaders.{
  ConfigLoader,
  PbsmrtpipeConfigLoader
}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.database.{
  DatabaseRunDaoProvider,
  DatabaseSampleDaoProvider,
  DatabaseUtils
}
import com.pacbio.secondary.smrtlink.models.{
  DataModelParserImplProvider,
  MimeTypeDetectors
}
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.common.logging.LoggerOptions
import com.pacbio.common.models.Constants
import com.pacbio.common.utils.OSUtils
import com.pacbio.secondary.smrtlink.actors.AlarmManagerRunnerActor.RunAlarms
import com.pacbio.secondary.smrtlink.actors.CommonMessages.GetEngineManagerStatus
import com.pacbio.secondary.smrtlink.actors.DataIntegrityManagerActor.RunIntegrityChecks
import com.pacbio.secondary.smrtlink.actors.DbBackupActor.SubmitDbBackUpJob
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.EngineManagerStatus
import com.pacbio.secondary.smrtlink.auth.JwtUtilsImplProvider
import com.pacbio.secondary.smrtlink.file.JavaFileSystemUtilProvider
import com.pacbio.secondary.smrtlink.services.utils.StatusGeneratorProvider
import com.pacbio.secondary.smrtlink.time.SystemClockProvider
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

object SmrtLinkApp

trait CoreProviders
    extends ActorSystemProvider
    with SetBindings
    with DefaultConfigProvider
    with ServiceRoutesProvider
    with ServiceManifestsProvider
    with ManifestServiceProvider
    with StatusServiceProvider
    with StatusGeneratorProvider
    with UserServiceProvider
    with CommonFilesServiceProvider
    with DiskSpaceServiceProvider
    with MimeTypeDetectors
    with JwtUtilsImplProvider
    with JavaFileSystemUtilProvider
    with SystemClockProvider
    with ConfigLoader {

  val serverPort: Singleton[Int] = Singleton(
    () => conf.getInt("smrtflow.server.port"))
  val serverHost: Singleton[String] = Singleton(
    () => conf.getString("smrtflow.server.host"))

  override val actorSystemName = Some("base-smrt-server")

  override val buildPackage: Singleton[Package] = Singleton(
    getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_common")

}

trait AuthenticatedCoreProviders
    extends ActorSystemProvider
    with SetBindings
    with DefaultConfigProvider
    with ServiceComposer
    with ManifestServiceProviderx
    with StatusServiceProviderx
    with StatusGeneratorProvider
    with UserServiceProviderx
    with CommonFilesServiceProviderx
    with DiskSpaceServiceProviderx
    with MimeTypeDetectors
    with JwtUtilsImplProvider
    with JavaFileSystemUtilProvider
    with SystemClockProvider
    with ConfigLoader {

  val serverPort: Singleton[Int] = Singleton(
    () => conf.getInt("smrtflow.server.port"))
  val serverHost: Singleton[String] = Singleton(
    () => conf.getString("smrtflow.server.host"))

  override val actorSystemName = Some("base-smrt-server")

  override val buildPackage: Singleton[Package] = Singleton(
    getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_common")
}

trait BaseApi extends LazyLogging {
  val providers: ActorSystemProvider with RouteProvider

  implicit lazy val system = providers.actorSystem()
  implicit lazy val materializer = ActorMaterializer()(system)
  lazy val routes: Route = providers.routes()
}

trait SmrtLinkProviders
    extends AuthenticatedCoreProviders
    with SmrtLinkConfigProvider
    with AlarmDaoActorProvider
    with AlarmRunnerLoaderProvider
    with AlarmManagerRunnerProvider
    with AlarmServiceProvider
    with SwaggerFileServiceProvider
    with PbsmrtpipeConfigLoader
    with PacBioBundleDaoActorProvider
    with PacBioDataBundlePollExternalActorProvider
    with PacBioBundleServiceProvider
    with EventManagerActorProvider
    with SmrtLinkEventServiceProvider
    with JobsDaoProvider
    with SmrtLinkDalProvider
    with DataIntegrityManagerActorProvider
    with EulaServiceProvider
    with ProjectServiceProvider
    with DataSetServiceProvider
    with RunServiceProvider
    with SampleServiceProvider
    with RegistryServiceProvider
    with RunServiceActorRefProvider
    with SampleServiceActorRefProvider
    with DatabaseRunDaoProvider
    with DatabaseSampleDaoProvider
    with InMemoryRegistryDaoProvider
    with DataModelParserImplProvider
    with SimpleLogServiceProvider
    with PipelineDataStoreViewRulesServiceProvider
    with PipelineTemplateProvider
    with ResolvedPipelineTemplateServiceProvider
    with PipelineTemplateViewRulesServiceProvider
    with ReportViewRulesResourceProvider
    with ReportViewRulesServiceProvider
    with JobsServiceProvider
    with EngineCoreJobManagerActorProvider
    with EngineMultiJobManagerActorProvider
    with DbBackupActorProvider {

  override val baseServiceId: Singleton[String] = Singleton(
    "smrtlink_analysis")
  override val actorSystemName = Some("smrtlink-analysis-server")
  override val buildPackage: Singleton[Package] = Singleton(
    getClass.getPackage)
}

trait SmrtLinkApi
    extends OSUtils
    with PacBioServiceErrors
    with LazyLogging
    with DatabaseUtils
    with ServiceLoggingUtils {

  lazy val providers = new SmrtLinkProviders {}

  implicit val customExceptionHandler = pacbioExceptionHandler
  implicit val customRejectionHandler = pacBioRejectionHandler

  // This is needed with Mixin routes from traits that only extend HttpService
  def actorRefFactory: ActorRefFactory = system

  implicit lazy val system = providers.actorSystem()
  lazy val scheduler = QuartzSchedulerExtension(system)
  implicit lazy val materializer = ActorMaterializer()(system)
  lazy val routes: Route = providers.routes()

  // Is this necessary because there's not an explicit dep on this?
  // Is there also friction with the lazy and Provider model?
  lazy val dataIntegrityManagerActor = providers.dataIntegrityManagerActor()
  lazy val alarmManagerRunnerActor = providers.alarmManagerRunnerActor()
  lazy val engineManagerActor = providers.engineManagerActor()
  lazy val engineMultiJobManagerActor = providers.engineMultiJobManagerActor()
  lazy val dbBackupActor = providers.dbBackupActor()

  lazy val eventManagerActor = providers.eventManagerActor()

  import EventManagerActor._

  private def createJobDir(path: Path): Path = {
    if (!Files.exists(path)) {
      logger.info(s"Creating root job dir $path")
      Files.createDirectories(path)
    }
    path.toAbsolutePath
  }

  /**
    * StartUp validation before the webservices are started
    *
    * This should validate the conf, validate/create necessary
    * resources, such as the job root and run db migrations.
    *
    * @param host
    * @param port
    * @return
    */
  private def preStart(host: String, port: Int): Future[String] = {
    logger.info(
      s"Starting App using smrtflow ${Constants.SMRTFLOW_VERSION} on $host:$port")
    logger.info(s"Running on OS ${getOsVersion()}")
    logger.info(
      s"Number of Available Processors ${Runtime.getRuntime().availableProcessors()}")
    logger.info("Java Version: " + System.getProperty("java.version"))
    logger.info("Java Home: " + System.getProperty("java.home"))
    val runtimeMxBean = ManagementFactory.getRuntimeMXBean
    val arguments = runtimeMxBean.getInputArguments
    logger.info("Java Args: " + arguments.asScala.mkString(" "))

    // Is this auto closing the datasource?
    //startUpValidation.andThen { case _ => dataSource.clo }
    val dataSource = providers.dbConfig.toDataSource
    logger.info(s"Database configuration ${providers.dbConfig}")

    // This might not be the best place for this
    // To avoid circular dependencies add listeners here
    lazy val jobsDao = providers.jobsDao()
    jobsDao.addListener(eventManagerActor)
    jobsDao.addListener(engineMultiJobManagerActor)

    // Start Up validation
    for {
      connMessage <- Future.successful(TestConnection(dataSource))
      migrationMessage <- Future.successful(Migrator(dataSource))
      summary <- providers.jobsDao().getSystemSummary("Database Startup Test")
      jobDir <- Future.successful(
        createJobDir(providers.engineConfig.pbRootJobDir))
    } yield
      s"""$connMessage
         |$migrationMessage
         |$summary
         |Created Job Root $jobDir
         |Loaded Pbsmrtpipe Workflow Level Options:
         |${providers
           .systemJobConfig()
           .pbSmrtPipeEngineOptions
           .summary()}""".stripMargin
  }

  //val routesLogged = DebuggingDirectives.logRequestResult("System", Logging.InfoLevel)(routes)

  /**
    * Fundamental Starting up of the WebServices
    */
  private def start(host: String, port: Int): Future[ServerBinding] =
    Http().bindAndHandle(logResponseTimeRoutes(routes), host, port)

  /**
    * Fundamental Post Startup hook
    */
  private def postStart(
      implicit timeout: Timeout = Timeout(10.seconds)): Future[String] = {
    // Setup Quartz Schedule
    // ALl the cron-esque tasks in SL should be folded back here
    // for consistency. For example, checking for chemistry bundle,
    // triggering alarm checks, checking status of event server,
    // data integrity checks.
    val alarmSchedule =
      providers.conf.getString("pacBioSystem.alarmSchedule")
    logger.info(s"Scheduling $alarmSchedule Alarm Manager Runner(s)")
    scheduler.schedule(alarmSchedule, alarmManagerRunnerActor, RunAlarms)

    val dataIntegritySchedule =
      providers.conf.getString("pacBioSystem.dataIntegritySchedule")
    logger.info(
      s"Scheduling $dataIntegritySchedule DataIntegrity Manager Runner")
    scheduler.schedule(dataIntegritySchedule,
                       dataIntegrityManagerActor,
                       RunIntegrityChecks)

    // These are all Unit return types
    providers.rootDataBaseBackUpDir() match {
      case Some(rootBackUpDir) =>
        val dbBackupKey =
          providers.conf.getString("pacBioSystem.dbBackUpSchedule")
        val desc =
          s"System created Database backup $dbBackupKey to $rootBackUpDir"
        val m = SubmitDbBackUpJob(System.getProperty("user.name"),
                                  s"System Scheduled backup",
                                  desc)
        logger.info(s"Scheduling '$dbBackupKey' db backup $m")
        scheduler.schedule(dbBackupKey, dbBackupActor, m)
      case _ =>
        logger.warn(
          "System is not configured with a root database directory. Skipping scheduling Automated backups.")
    }

    def getInstallMetrics(): Future[Boolean] =
      providers.smrtLinkVersion() match {
        case Some(v) =>
          providers
            .jobsDao()
            .getEulaByVersion(v)
            .map(_.enableInstallMetrics)
            .recover({ case _ => false })
        case _ => Future.successful(false)
      }

    // This should have a better status message
    for {
      engineManagerStatus <- (engineManagerActor ? GetEngineManagerStatus)
        .mapTo[EngineManagerStatus]
      installMetric <- getInstallMetrics()
      msg <- (eventManagerActor ? EnableExternalMessages(installMetric))
        .mapTo[String]
    } yield s"""$engineManagerStatus\n$msg"""
  }

  /**
    * This will startup the system
    *
    */
  def startUpSystem(host: String, port: Int): Future[String] = {
    for {
      preStartMessage <- preStart(host, port)
      startMessage <- start(host, port)
      postStartMessage <- postStart()
    } yield s"""
        |$preStartMessage
         $startMessage
         $postStartMessage
      """.stripMargin
  }

  def shutdown(msg: Option[String] = None): Unit = {
    logger.info(s"Shutting down system ${msg.getOrElse("")}")
    //FIXME(mpkocher)(1-3-2017) This should also close the db connections
    system.terminate()
  }
}

/**
  * This is used for spray-can http server which can be started via 'sbt run'
  */
object SmrtLinkSmrtServer extends App with SmrtLinkApi with LazyLogging {
  lazy val host = providers.serverHost()
  lazy val port = providers.serverPort()

  // Not sure if this is the greatest place for this.
  sys.addShutdownHook(system.terminate())

  LoggerOptions.parseAddDebug(args)

  Try(Await.result(startUpSystem(host, port), Duration.Inf)) match {
    case Success(msg) => logger.info(msg)
    case Failure(ex) =>
      val msg = s"Failed to startup system ${ex.getMessage}"
      System.err.println(msg)
      logger.error(msg)
      shutdown()
      System.exit(1)
  }

}
