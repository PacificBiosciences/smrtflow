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
  implicit val helloWorldJobOptionJsonFormat = jsonFormat6(
    HelloWorldJobOptions)
  implicit val deleteDataSetobOptionJsonFormat = jsonFormat8(
    DeleteDataSetJobOptions)

  // These are from NEW JobType Option data model

  implicit val mergeDataSetJobOptionJsonFormat = jsonFormat7(
    MergeDataSetJobOptions)

  // This is defined in SmrtLinkJsonProtocols
  // implicit val importDataSetJobOptionJsonFormat = jsonFormat6(ImportDataSetJobOptions)

  implicit val importDataSetsZipJobOptionsFormat = jsonFormat6(
    ImportDataSetsZipJobOptions)
  implicit val exportDataSetJobOptionJsonFormat = jsonFormat9(
    ExportDataSetsJobOptions)
  implicit val exportAnalysisJobOptionsFormat = jsonFormat8(
    ExportSmrtLinkJobOptions)
  implicit val importJobOptionsFormat = jsonFormat7(ImportSmrtLinkJobOptions)
  implicit val importBarcodeFastaJobOptionsJsonFormat = jsonFormat6(
    ImportBarcodeFastaJobOptions)
  implicit val importFastaJobOptionsJsonFormat = jsonFormat8(
    ImportFastaJobOptions)
  implicit val importFastaGmapJobOptionsJsonFormat = jsonFormat8(
    ImportFastaGmapJobOptions)

  // Renaming workaround
  implicit val pbsmrtpipeJobOptionsJsonFormat = jsonFormat9(
    PbsmrtpipeJobOptions)
  implicit val mockPbsmrtpipeJobOptionsJsonFormat = jsonFormat9(
    MockPbsmrtpipeJobOptions)

  implicit val deleteSmrtLinkJobOptionsFormat = jsonFormat9(
    DeleteSmrtLinkJobOptions)
  implicit val helloWorldJobOptionsFormat = jsonFormat6(SimpleJobOptions)

  implicit val rsConvertMovieToDataSetJobOptionsFormat = jsonFormat6(
    RsConvertMovieToDataSetJobOptions)

  implicit val techSupportSystemBundleJobOptionsFormat = jsonFormat7(
    TsSystemStatusBundleJobOptions)
  implicit val techSupportJobHarvesterJobOptionsFormat = jsonFormat7(
    TsJobHarvesterJobOptions)
  implicit val techSupportJobBundleJobOptionsFormat = jsonFormat8(
    TsJobBundleJobOptions)

  implicit val entryPointDeferredJobFormat = jsonFormat3(DeferredEntryPoint)
  implicit val deferredJobFormat = jsonFormat7(DeferredJob)
  implicit val multiAnalysisJobOptionsFormat = jsonFormat6(
    MultiAnalysisJobOptions)

  implicit val dbBackUpJobOptionsFormat = jsonFormat7(DbBackUpJobOptions)
  implicit val copyDataSetJobOptionsFormat = jsonFormat7(CopyDataSetJobOptions)
}

object ServiceJobTypeJsonProtocols extends ServiceJobTypeJsonProtocols
