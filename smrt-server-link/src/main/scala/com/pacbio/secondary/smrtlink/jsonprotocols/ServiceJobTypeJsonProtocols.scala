package com.pacbio.secondary.smrtlink.jsonprotocols

import com.pacbio.secondary.smrtlink.jobtypes._
import com.pacbio.secondary.smrtlink.models.{DeferredEntryPoint, DeferredJob}
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFilterProperty

import fommil.sjs.FamilyFormats
import spray.json.DefaultJsonProtocol

/**
  * Created by mkocher on 8/22/17.
  */
trait ServiceJobTypeJsonProtocols
    extends DefaultJsonProtocol
    with FamilyFormats {

  import SmrtLinkJsonProtocols._
  // put these here for now
  implicit val helloWorldJobOptionJsonFormat = jsonFormat5(
    HelloWorldJobOptions)
  implicit val deleteDataSetobOptionJsonFormat = jsonFormat7(
    DeleteDataSetJobOptions)

  // These are from NEW JobType Option data model

  implicit val mergeDataSetJobOptionJsonFormat = jsonFormat6(
    MergeDataSetJobOptions)
  //implicit val importDataSetJobOptionJsonFormat = jsonFormat5(ImportDataSetJobOptions)

  implicit val exportDataSetJobOptionJsonFormat = jsonFormat8(
    ExportDataSetsJobOptions)
  implicit val exportAnalysisJobOptionsFormat = jsonFormat7(
    ExportSmrtLinkJobOptions)
  implicit val importJobOptionsFormat = jsonFormat6(ImportSmrtLinkJobOptions)
  implicit val importBarcodeFastaJobOptionsJsonFormat = jsonFormat5(
    ImportBarcodeFastaJobOptions)
  implicit val importFastaJobOptionsJsonFormat = jsonFormat7(
    ImportFastaJobOptions)
  implicit val importFastaGmapJobOptionsJsonFormat = jsonFormat7(
    ImportFastaGmapJobOptions)

  // Renaming workaround
  implicit val pbsmrtpipeJobOptionsJsonFormat = jsonFormat8(
    PbsmrtpipeJobOptions)
  implicit val mockPbsmrtpipeJobOptionsJsonFormat = jsonFormat8(
    MockPbsmrtpipeJobOptions)

  implicit val deleteSmrtLinkJobOptionsFormat = jsonFormat8(
    DeleteSmrtLinkJobOptions)
  implicit val helloWorldJobOptionsFormat = jsonFormat5(SimpleJobOptions)

  implicit val rsConvertMovieToDataSetJobOptionsFormat = jsonFormat5(
    RsConvertMovieToDataSetJobOptions)

  implicit val techSupportSystemBundleJobOptionsFormat = jsonFormat6(
    TsSystemStatusBundleJobOptions)
  implicit val techSupportJobHarvesterJobOptionsFormat = jsonFormat6(
    TsJobHarvesterJobOptions)
  implicit val techSupportJobBundleJobOptionsFormat = jsonFormat7(
    TsJobBundleJobOptions)

  implicit val entryPointDeferredJobFormat = jsonFormat3(DeferredEntryPoint)
  implicit val deferredJobFormat = jsonFormat7(DeferredJob)
  implicit val multiAnalysisJobOptionsFormat = jsonFormat5(
    MultiAnalysisJobOptions)

  implicit val dbBackUpJobOptionsFormat = jsonFormat6(DbBackUpJobOptions)
  implicit val copyDataSetJobOptionsFormat = jsonFormat6(CopyDataSetJobOptions)
}

object ServiceJobTypeJsonProtocols extends ServiceJobTypeJsonProtocols
