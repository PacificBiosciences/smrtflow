package com.pacbio.secondary.smrtlink.app

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.logging.{LogResources, LoggerFactoryProvider}
import com.pacbio.secondary.smrtlink.models.LogResourceRecord
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
  with TsJobBundleJobServiceTypeProvider
  with TsSystemStatusBundleServiceTypeProvider {

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_analysis")
  override val actorSystemName = Some("smrtlink-analysis-server")
  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)
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
