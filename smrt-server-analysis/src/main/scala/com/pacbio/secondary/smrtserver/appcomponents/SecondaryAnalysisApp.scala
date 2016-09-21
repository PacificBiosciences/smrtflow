package com.pacbio.secondary.smrtserver.appcomponents

import com.pacbio.common.app.BaseServer
import com.pacbio.common.dependency.{TypesafeSingletonReader, Singleton}
import com.pacbio.common.logging.{LoggerFactoryProvider, LogResources}
import com.pacbio.common.models.LogResourceRecord
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.smrtlink.app.{SmrtLinkApi, SmrtLinkProviders}
import com.pacbio.secondary.smrtserver.services._
import com.pacbio.secondary.smrtserver.services.jobtypes._
import com.typesafe.scalalogging.LazyLogging

trait SecondaryAnalysisProviders
  extends SmrtLinkProviders
  with PipelineDataStoreViewRulesServiceProvider
  with PipelineTemplateProvider
  with ResolvedPipelineTemplateServiceProvider
  with PipelineTemplateViewRulesServiceProvider
  with ReportViewRulesResourceProvider
  with ReportViewRulesServiceProvider
  with ImportDataStoreServiceTypeProvider
  with ImportFastaServiceTypeProvider
  with ImportFastaBarcodesServiceTypeProvider
  with PbsmrtpipeServiceJobTypeProvider
  with RsConvertMovieToDataSetServiceTypeProvider
  with SimpleServiceJobTypeProvider
  with ExportDataSetsServiceJobTypeProvider
  with LoggerFactoryProvider {

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_analysis")
  override val actorSystemName = Some("smrtlink-analysis-server")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  val pbServices = TypesafeSingletonReader.fromConfig().in("pb-services")

  // MK. Listen to all. See bug 29715. The 'host' and HOST in config.json is now confusingly named. The
  // value of host is used to create the Service callback/update URI for pbsmrtpipe.
  override val serverHost: Singleton[String] = Singleton("0.0.0.0")
  override val serverPort: Singleton[Int] = pbServices.getInt("port").orElse(8071)

  Singleton(LogResourceRecord("pbsmrtpipe Analysis Jobs", "pbsmrtpipe", "pbsmrtpipe jobs")).bindToSet(LogResources)
  Singleton(LogResourceRecord("SMRT Link UI", "smrtlink", "SMRTLink UI")).bindToSet(LogResources)
}

trait SecondaryApi extends SmrtLinkApi with LazyLogging {
  override val providers = new SecondaryAnalysisProviders {}

  sys.addShutdownHook(system.shutdown())
}

object SecondaryAnalysisServer extends App with BaseServer with SecondaryApi {
  override val host = providers.serverHost()
  override val port = providers.serverPort()

  LoggerOptions.parseAddDebug(args)

  start
}
