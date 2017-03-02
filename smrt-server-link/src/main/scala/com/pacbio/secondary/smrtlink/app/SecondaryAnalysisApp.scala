package com.pacbio.secondary.smrtlink.app

import com.pacbio.common.app.BaseServer
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LogResources, LoggerFactoryProvider}
import com.pacbio.common.models.LogResourceRecord
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.secondary.smrtlink.services.jobtypes._
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
  with DeleteDataSetsServiceJobTypeProvider
  with LoggerFactoryProvider {

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_analysis")
  override val actorSystemName = Some("smrtlink-analysis-server")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

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
